/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.model

import java.io.IOException

sealed interface LoginFailure {
    data class PortBusy(val port: Int) : LoginFailure
    data object InvalidState : LoginFailure
    data object MissingCode : LoginFailure
    data object CodeMismatch : LoginFailure
    data object Expired : LoginFailure
    data object Denied : LoginFailure
    data class Http(val code: Int, val detail: String?) : LoginFailure
    data class Other(val detail: String) : LoginFailure
}

class LoginFailedException(val failure: LoginFailure) : IOException(failure.toString())
