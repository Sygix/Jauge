/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.auth

import android.content.Context
import android.text.TextUtils
import fr.sygix.jauge.R
import fr.sygix.jauge.model.Provider

object SuccessPage {
    const val RETURN_LINK = "intent://auth#Intent;scheme=jauge;package=fr.sygix.jauge;end"

    fun html(context: Context, provider: Provider): String {
        val accent = String.format("#%06X", provider.accent and 0xFFFFFF)
        val lang = context.resources.configuration.locales[0]?.language ?: "en"
        val title = TextUtils.htmlEncode(context.getString(R.string.success_title, provider.displayName))
        val body = TextUtils.htmlEncode(context.getString(R.string.success_body))
        val button = TextUtils.htmlEncode(context.getString(R.string.success_button))
        return """
            <!doctype html>
            <html lang="$lang"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Jauge</title>
            <style>
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font-family:system-ui,sans-serif;background:#111318;color:#e6e7ec}
            .card{max-width:340px;padding:32px 24px;text-align:center}
            .ring{width:72px;height:72px;border-radius:50%;border:6px solid $accent;margin:0 auto 20px;display:flex;align-items:center;justify-content:center;font-size:32px;color:$accent}
            h1{font-size:22px;margin:0 0 8px}p{color:#a9acb6;margin:0 0 28px;line-height:1.4}
            a{display:inline-block;padding:14px 28px;border-radius:999px;background:$accent;color:#111318;text-decoration:none;font-weight:600}
            </style></head>
            <body><div class="card"><div class="ring">✓</div>
            <h1>$title</h1>
            <p>$body</p>
            <a href="$RETURN_LINK">$button</a></div>
            <script>setTimeout(function(){location.href="$RETURN_LINK"},400)</script>
            </body></html>
        """.trimIndent()
    }
}
