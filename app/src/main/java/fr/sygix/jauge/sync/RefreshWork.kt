/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.sygix.jauge.graph
import fr.sygix.jauge.store.Settings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.graph
        graph.sync.syncAll()
        if (inputData.getBoolean(KEY_LOOP, false)) {
            RefreshScheduler.enqueueLoop(applicationContext, graph.store.settings.first(), ExistingWorkPolicy.APPEND_OR_REPLACE, delayed = true)
        }
        return Result.success()
    }

    companion object {
        const val KEY_LOOP = "loop"
    }
}

object RefreshScheduler {
    private const val LOOP = "jauge-refresh-loop"
    private const val NOW = "jauge-refresh-now"

    suspend fun ensure(context: Context) {
        enqueueLoop(context, context.graph.store.settings.first(), ExistingWorkPolicy.KEEP, delayed = true)
    }

    fun restart(context: Context, settings: Settings) {
        enqueueLoop(context, settings, ExistingWorkPolicy.REPLACE, delayed = false)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun enqueueLoop(context: Context, settings: Settings, policy: ExistingWorkPolicy, delayed: Boolean) {
        val network = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(if (delayed) settings.refreshMinutes.toLong() else 0L, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setInputData(workDataOf(RefreshWorker.KEY_LOOP to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(LOOP, policy, request)
    }
}
