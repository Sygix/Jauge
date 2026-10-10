/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.auth

import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class LoopbackServer private constructor(
    private val sockets: List<ServerSocket>,
    private val path: String,
    private val successPage: String,
) : Closeable {

    val port: Int get() = sockets.first().localPort

    private val result = CompletableDeferred<Map<String, String>>()

    suspend fun awaitCallback(): Map<String, String> = result.await()

    private fun start(scope: CoroutineScope) {
        sockets.forEach { socket ->
            scope.launch(Dispatchers.IO) {
                while (!socket.isClosed && !result.isCompleted) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    runCatching { handle(client) }
                }
            }
        }
    }

    private fun handle(client: Socket) = client.use { socket ->
        socket.soTimeout = 5_000
        val reader = socket.getInputStream().bufferedReader()
        val requestLine = reader.readLine().orEmpty()
        while (reader.readLine()?.isNotEmpty() == true) Unit
        val target = requestLine.split(" ").getOrNull(1).orEmpty()
        val url = "http://localhost$target".toHttpUrlOrNull()
        val out = socket.getOutputStream()
        if (url == null || url.encodedPath != path) {
            out.write(response(404, "Not found").toByteArray())
            return@use
        }
        val params = url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() }
        out.write(response(200, successPage).toByteArray())
        out.flush()
        result.complete(params)
    }

    private fun response(status: Int, body: String): String {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else "Not Found"
        return "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n$body"
    }

    override fun close() {
        sockets.forEach { runCatching { it.close() } }
        result.cancel()
    }

    companion object {
        fun start(scope: CoroutineScope, port: Int, path: String, successPage: String): LoopbackServer {
            val primary = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
            }
            val secondary = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("::1"), primary.localPort))
                }
            }.getOrNull()
            return LoopbackServer(listOfNotNull(primary, secondary), path, successPage).also { it.start(scope) }
        }
    }
}
