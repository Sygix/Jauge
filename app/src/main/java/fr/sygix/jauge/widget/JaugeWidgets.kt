/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import fr.sygix.jauge.sync.RefreshScheduler

object JaugeWidgets {
    suspend fun updateAll(context: Context) {
        ClaudeWidget().updateAll(context)
        CodexWidget().updateAll(context)
        NousWidget().updateAll(context)
        OverviewWidget().updateAll(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}
