/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.auth

import android.content.Context
import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.LoginFailedException
import fr.sygix.jauge.model.LoginFailure
import fr.sygix.jauge.model.Provider
import fr.sygix.jauge.net.HttpException
import fr.sygix.jauge.provider.ClaudeAuth
import fr.sygix.jauge.provider.CodexAuth
import fr.sygix.jauge.provider.DeviceCode
import fr.sygix.jauge.provider.DevicePoll
import fr.sygix.jauge.provider.NousAuth
import fr.sygix.jauge.provider.OpenRouterAuth
import fr.sygix.jauge.provider.OpenCodeAuth
import fr.sygix.jauge.sync.UsageSync
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface LoginState {
    data object Idle : LoginState
    data class WaitingBrowser(val provider: Provider, val url: String) : LoginState
    data class WaitingManualCode(val provider: Provider, val url: String) : LoginState
    data class WaitingApiKey(val provider: Provider, val failure: LoginFailure? = null, val validating: Boolean = false) : LoginState
    data class RequestingDevice(val provider: Provider) : LoginState
    data class WaitingDevice(val provider: Provider, val code: DeviceCode) : LoginState
    data class Finishing(val provider: Provider) : LoginState
    data class Success(val provider: Provider) : LoginState
    data class Failed(val provider: Provider, val failure: LoginFailure) : LoginState
}

class LoginBroker(private val context: Context, private val sync: UsageSync) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var server: LoopbackServer? = null
    private var manual: ManualSession? = null

    private data class ManualSession(val pkce: Pkce, val state: String)

    fun startBrowser(provider: Provider): String? {
        require(provider in setOf(Provider.CLAUDE, Provider.CODEX, Provider.OPENROUTER))
        cancel()
        val pkce = Pkce.create()
        val oauthState = Pkce.randomToken()
        val port = if (provider == Provider.CODEX) CodexAuth.LOOPBACK_PORT else 0
        val path = if (provider == Provider.CODEX) "/auth/callback" else "/callback"
        val loopback = try {
            LoopbackServer.start(scope, port, path, SuccessPage.html(context, provider))
        } catch (e: IOException) {
            mutableState.value = LoginState.Failed(provider, LoginFailure.PortBusy(port))
            return null
        }
        server = loopback
        val redirectUri = when (provider) {
            Provider.CLAUDE -> ClaudeAuth.loopbackRedirectUri(loopback.port)
            Provider.OPENROUTER -> OpenRouterAuth.loopbackRedirectUri(loopback.port)
            else -> CodexAuth.REDIRECT_URI
        }
        val url = when (provider) {
            Provider.CLAUDE -> ClaudeAuth.authorizeUrl(pkce, oauthState, redirectUri, manual = false)
            Provider.CODEX -> CodexAuth.authorizeUrl(pkce, oauthState)
            Provider.OPENROUTER -> OpenRouterAuth.authorizeUrl(pkce, oauthState, redirectUri)
            else -> error("Unsupported browser sign-in")
        }
        LoginService.start(context)
        mutableState.value = LoginState.WaitingBrowser(provider, url)
        job = scope.launch {
            try {
                val params = withTimeout(TIMEOUT_MS) { loopback.awaitCallback() }
                params["error"]?.let { throw LoginFailedException(LoginFailure.Other(params["error_description"]?.ifBlank { null } ?: it)) }
                if (params["state"] != oauthState) throw LoginFailedException(LoginFailure.InvalidState)
                val code = params["code"]?.takeIf { it.isNotBlank() } ?: throw LoginFailedException(LoginFailure.MissingCode)
                mutableState.value = LoginState.Finishing(provider)
                val credentials = when (provider) {
                    Provider.CLAUDE -> ClaudeAuth.exchange(code, oauthState, pkce.verifier, redirectUri)
                    Provider.CODEX -> CodexAuth.exchange(code, pkce.verifier)
                    Provider.OPENROUTER -> OpenRouterAuth.exchange(code, pkce.verifier)
                    else -> error("Unsupported browser sign-in")
                }
                finish(provider, credentials)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = LoginState.Failed(provider, describe(e, provider))
            } finally {
                loopback.close()
                LoginService.stop(context)
            }
        }
        return url
    }

    fun startManualClaude(): String {
        cancel()
        val session = ManualSession(Pkce.create(), Pkce.randomToken())
        manual = session
        val url = ClaudeAuth.authorizeUrl(session.pkce, session.state, ClaudeAuth.MANUAL_REDIRECT_URI, manual = true)
        mutableState.value = LoginState.WaitingManualCode(Provider.CLAUDE, url)
        return url
    }

    fun submitManualCode(input: String) {
        val session = manual ?: return
        val parts = input.trim().split("#")
        val code = parts[0].trim()
        val receivedState = parts.getOrNull(1)?.trim().orEmpty()
        if (code.isEmpty()) return
        if (receivedState.isNotEmpty() && receivedState != session.state) {
            mutableState.value = LoginState.Failed(Provider.CLAUDE, LoginFailure.CodeMismatch)
            return
        }
        job?.cancel()
        job = scope.launch {
            try {
                mutableState.value = LoginState.Finishing(Provider.CLAUDE)
                val credentials = ClaudeAuth.exchange(code, session.state, session.pkce.verifier, ClaudeAuth.MANUAL_REDIRECT_URI)
                manual = null
                finish(Provider.CLAUDE, credentials)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = LoginState.Failed(Provider.CLAUDE, describe(e))
            }
        }
    }

    fun startDevice(provider: Provider) {
        require(provider in setOf(Provider.CODEX, Provider.NOUS, Provider.OPENCODE))
        cancel()
        LoginService.start(context)
        mutableState.value = LoginState.RequestingDevice(provider)
        job = scope.launch {
            try {
                val code = when (provider) {
                    Provider.CODEX -> CodexAuth.requestDeviceCode()
                    Provider.NOUS -> NousAuth.requestDeviceCode()
                    Provider.OPENCODE -> OpenCodeAuth.requestDeviceCode()
                    else -> error("Unsupported device code flow")
                }
                mutableState.value = LoginState.WaitingDevice(provider, code)
                var interval = code.intervalSeconds
                while (System.currentTimeMillis() < code.expiresAtMs) {
                    delay(interval * 1000L)
                    val poll = when (provider) {
                        Provider.CODEX -> CodexAuth.pollDevice(code)
                        Provider.NOUS -> NousAuth.pollDevice(code)
                        Provider.OPENCODE -> OpenCodeAuth.pollDevice(code)
                        else -> error("Unsupported device code flow")
                    }
                    when (poll) {
                        DevicePoll.Pending -> Unit
                        DevicePoll.SlowDown -> interval += 5
                        is DevicePoll.Done -> {
                            finish(provider, poll.credentials)
                            return@launch
                        }
                    }
                }
                throw LoginFailedException(LoginFailure.Expired)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = LoginState.Failed(provider, describe(e, provider))
            } finally {
                LoginService.stop(context)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        server?.close()
        server = null
        manual = null
        LoginService.stop(context)
        mutableState.value = LoginState.Idle
    }

    fun startApiKey(provider: Provider) {
        require(provider == Provider.OPENROUTER || provider == Provider.OPENCODE)
        cancel()
        mutableState.value = LoginState.WaitingApiKey(provider)
    }

    fun submitApiKey(input: String) {
        val state = mutableState.value as? LoginState.WaitingApiKey ?: return
        val key = input.trim()
        if (state.validating || key.isBlank() || key.any { it.isWhitespace() || it.isISOControl() }) return
        mutableState.value = state.copy(validating = true, failure = null)
        job = scope.launch {
            try {
                sync.connectValidated(state.provider, Credentials(accessToken = key))
                mutableState.value = LoginState.Success(state.provider)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = state.copy(failure = describe(e, state.provider))
            }
        }
    }

    fun acknowledge() {
        val current = mutableState.value
        if (current is LoginState.Success || current is LoginState.Failed) mutableState.value = LoginState.Idle
    }

    private suspend fun finish(provider: Provider, credentials: Credentials) {
        mutableState.value = LoginState.Finishing(provider)
        if (provider == Provider.OPENROUTER || provider == Provider.OPENCODE) sync.connectValidated(provider, credentials)
        else sync.connect(provider, credentials)
        mutableState.value = LoginState.Success(provider)
    }

    private fun describe(e: Throwable, provider: Provider? = null): LoginFailure = when (e) {
        is LoginFailedException -> e.failure
        is HttpException -> LoginFailure.Http(e.code, if (provider == Provider.OPENROUTER || provider == Provider.OPENCODE) null else e.oauthError ?: e.body.take(160).takeIf { it.isNotBlank() })
        else -> LoginFailure.Other(UsageSync.describe(e))
    }

    private companion object {
        const val TIMEOUT_MS = 10 * 60 * 1000L
    }
}
