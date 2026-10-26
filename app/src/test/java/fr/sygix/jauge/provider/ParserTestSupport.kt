/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.net.Http
import kotlinx.serialization.json.jsonObject

internal fun parserJson(value: String) = Http.json.parseToJsonElement(value).jsonObject
