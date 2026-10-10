/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JaugeTheme { MainScreen(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun JaugeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

object Links {
    fun open(context: Context, url: String, toolbarColor: Int? = null) {
        val uri: Uri = url.toUri()
        val builder = CustomTabsIntent.Builder().setShowTitle(true)
        toolbarColor?.let { builder.setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(it).build()) }
        try {
            builder.build().launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun pinWidget(context: Context, receiver: Class<*>): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    }
}
