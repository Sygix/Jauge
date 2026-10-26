/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import fr.sygix.jauge.graph
import fr.sygix.jauge.store.Settings
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = applicationContext.graph
            graph.sync.syncAll()
            if (inputData.getBoolean(KEY_LOOP, false)) {
                RefreshScheduler.enqueueLoop(applicationContext, graph.store.settings.first(), ExistingWorkPolicy.APPEND_OR_REPLACE, delayed = true).await()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_LOOP = "loop"
    }
}

class RefreshRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        RefreshScheduler.enqueueLoop(applicationContext, applicationContext.graph.store.settings.first(), ExistingWorkPolicy.KEEP, delayed = false).await()
        return Result.success()
    }
}

object RefreshScheduler {
    private const val LOOP = "jauge-refresh-loop"
    private const val NOW = "jauge-refresh-now"
    private const val RECOVERY = "jauge-refresh-recovery"

    suspend fun ensure(context: Context) {
        val settings = context.graph.store.settings.first()
        enqueueRecovery(context, settings).await()
        enqueueLoop(context, settings, ExistingWorkPolicy.KEEP, delayed = true).await()
    }

    fun restart(context: Context, settings: Settings) {
        enqueueRecovery(context, settings)
        enqueueLoop(context, settings, ExistingWorkPolicy.REPLACE, delayed = false)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun enqueueLoop(context: Context, settings: Settings, policy: ExistingWorkPolicy, delayed: Boolean): Operation {
        val network = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(if (delayed) settings.refreshMinutes.toLong() else 0L, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setInputData(workDataOf(RefreshWorker.KEY_LOOP to true))
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork(LOOP, policy, request)
    }

    private fun enqueueRecovery(context: Context, settings: Settings): Operation {
        val network = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<RefreshRecoveryWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        return WorkManager.getInstance(context).enqueueUniquePeriodicWork(RECOVERY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
