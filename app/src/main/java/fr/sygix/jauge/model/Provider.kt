/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.model

enum class Provider(val displayName: String, val shortName: String, val accent: Int, val accentSoft: Int) {
    CLAUDE("Claude", "Claude", 0xFFD97757.toInt(), 0xFFF0B49E.toInt()),
    CODEX("ChatGPT Codex", "Codex", 0xFF10A37F.toInt(), 0xFF7FD6BC.toInt()),
    NOUS("Nous Portal", "Nous", 0xFF7C6CF2.toInt(), 0xFFB9B0FA.toInt()),
}
