/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import fr.sygix.jauge.store.Settings
import fr.sygix.jauge.store.TextTone

data class WidgetColors(
    val text: ColorProvider,
    val muted: ColorProvider,
    val error: ColorProvider,
    val background: ColorProvider?,
) {
    companion object {
        val LightText = WidgetColors(fixed(0xFFFFFFFF.toInt()), fixed(0xD9FFFFFF.toInt()), fixed(0xFFFF8A80.toInt()), null)
        val DarkText = WidgetColors(fixed(0xFF15171C.toInt()), fixed(0xC215171C.toInt()), fixed(0xFFB3261E.toInt()), null)
    }
}

val LocalWidgetColors = staticCompositionLocalOf { WidgetColors.LightText }

@Composable
fun WidgetSurface(settings: Settings, content: @Composable () -> Unit) {
    GlanceTheme {
        val colors = if (settings.widgetBackground) {
            WidgetColors(GlanceTheme.colors.onSurface, GlanceTheme.colors.onSurfaceVariant, GlanceTheme.colors.error, GlanceTheme.colors.widgetBackground)
        } else {
            when (settings.textTone) {
                TextTone.LIGHT -> WidgetColors.LightText
                TextTone.DARK -> WidgetColors.DarkText
                TextTone.AUTO -> if (Wallpaper.prefersDarkText(LocalContext.current)) WidgetColors.DarkText else WidgetColors.LightText
            }
        }
        CompositionLocalProvider(LocalWidgetColors provides colors, content = content)
    }
}

fun GlanceModifier.widgetBackground(colors: WidgetColors): GlanceModifier =
    colors.background?.let { background(it).cornerRadius(24.dp) } ?: this

object Wallpaper {
    fun prefersDarkText(context: Context): Boolean = runCatching {
        val hints = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.colorHints ?: 0
        hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    }.getOrDefault(false)
}
