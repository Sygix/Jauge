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
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import fr.sygix.jauge.graph
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.store.Settings
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = applicationContext.graph
            if (inputData.getBoolean(KEY_LOOP, false)) {
                val provider = Provider.entries.firstOrNull { it.name == inputData.getString(KEY_PROVIDER) }
                    ?: return Result.success()
                val settings = graph.store.settings.first()
                if (settings.refreshMinutes(provider) == null || provider !in graph.store.connected.first()) return Result.success()
                graph.sync.sync(provider)
                RefreshScheduler.enqueueNext(applicationContext, provider)
            } else {
                graph.sync.syncAll()
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
        const val KEY_PROVIDER = "provider"
    }
}

class RefreshRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        RefreshScheduler.ensure(applicationContext)
        return Result.success()
    }
}

object RefreshScheduler {
    private const val LOOP = "jauge-refresh-loop"
    private const val NOW = "jauge-refresh-now"
    private const val RECOVERY = "jauge-refresh-recovery"
    private val reconciler = RefreshReconciler()

    private suspend fun state(context: Context) = RefreshState(context.graph.store.settings.first(), context.graph.store.connected.first())

    suspend fun ensure(context: Context) = reconciler.run({ state(context) }) { (settings, connected) ->
        WorkManager.getInstance(context).cancelUniqueWork(LOOP).await()
        enqueueRecovery(context, settings).await()
        Provider.entries.forEach { provider ->
            if (provider in connected) enqueueLoop(context, settings, provider, ExistingWorkPolicy.KEEP).await()
            else WorkManager.getInstance(context).cancelUniqueWork(loopName(provider)).await()
        }
    }

    suspend fun restart(context: Context, previous: Settings) = reconciler.run({ state(context) }) { (settings, connected) ->
        enqueueRecovery(context, settings).await()
        connected.forEach { provider ->
            if (previous.refreshMinutes(provider) != settings.refreshMinutes(provider) || previous.wifiOnly != settings.wifiOnly) {
                enqueueLoop(context, settings, provider, ExistingWorkPolicy.REPLACE).await()
            }
        }
    }

    internal suspend fun enqueueNext(context: Context, provider: Provider) = reconciler.run({ state(context) }) { (settings, connected) ->
        if (provider in connected) enqueueLoop(context, settings, provider, ExistingWorkPolicy.APPEND_OR_REPLACE).await()
        else WorkManager.getInstance(context).cancelUniqueWork(loopName(provider)).await()
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun loopName(provider: Provider) = "$LOOP-${provider.name}"

    internal fun loopRequest(settings: Settings, provider: Provider): OneTimeWorkRequest? {
        val minutes = settings.refreshMinutes(provider) ?: return null
        val network = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        return OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setInputData(workDataOf(RefreshWorker.KEY_LOOP to true, RefreshWorker.KEY_PROVIDER to provider.name))
            .build()
    }

    private fun enqueueLoop(context: Context, settings: Settings, provider: Provider, policy: ExistingWorkPolicy): Operation {
        val manager = WorkManager.getInstance(context)
        val request = loopRequest(settings, provider) ?: return manager.cancelUniqueWork(loopName(provider))
        return manager.enqueueUniqueWork(loopName(provider), policy, request)
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
