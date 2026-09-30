package com.example.network

import android.net.Uri
import android.util.Base64
import com.example.network.model.RtspTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

object RtspValidator {

    private const val SOCKET_TIMEOUT_MS = 2500

    /**
     * Valida uma URL RTSP conectando-se diretamente ao servidor RTSP e enviando comando OPTIONS e DESCRIBE.
     */
    suspend fun validateRtspStream(
        ip: String,
        port: Int = 554,
        path: String = "/onvif1",
        user: String = "admin",
        pass: String = ""
    ): RtspTestResult = withContext(Dispatchers.IO) {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val fullUrl = buildRtspUrl(ip, port, cleanPath, user, pass)
        val startTime = System.currentTimeMillis()

        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS

            val out: OutputStream = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            val authHeader = if (user.isNotBlank() && pass.isNotBlank()) {
                val credentials = "$user:$pass"
                val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "Authorization: Basic $encoded\r\n"
            } else ""

            // Etapa 1: Envia DESCRIBE para verificar se o caminho e autenticação são aceitos
            val describeRequest = "DESCRIBE rtsp://$ip:$port$cleanPath RTSP/1.0\r\n" +
                    "CSeq: 1\r\n" +
                    "User-Agent: CamScanIP-Mobile/2.0\r\n" +
                    "Accept: application/sdp\r\n" +
                    authHeader +
                    "\r\n"

            out.write(describeRequest.toByteArray(Charsets.UTF_8))
            out.flush()

            val responseLines = mutableListOf<String>()
            var line: String? = reader.readLine()
            var statusLine = line ?: ""
            var statusCode: Int? = null
            var serverHeader: String? = null
            var publicMethods: String? = null

            while (line != null) {
                responseLines.add(line)
                val trimmed = line.trim()
                if (trimmed.isEmpty()) break

                if (trimmed.startsWith("RTSP/1.0") || trimmed.startsWith("RTSP/2.0")) {
                    statusLine = trimmed
                    val parts = trimmed.split(" ")
                    if (parts.size >= 2) {
                        statusCode = parts[1].toIntOrNull()
                    }
                } else if (trimmed.startsWith("Server:", ignoreCase = true)) {
                    serverHeader = trimmed.substringAfter(":").trim()
                } else if (trimmed.startsWith("Public:", ignoreCase = true)) {
                    publicMethods = trimmed.substringAfter(":").trim()
                }

                line = reader.readLine()
            }

            // Se DESCRIBE não respondeu ou deu 404, tenta OPTIONS para saber se a porta RTSP responde
            if (statusCode == null || statusCode == 405) {
                val optionsRequest = "OPTIONS rtsp://$ip:$port$cleanPath RTSP/1.0\r\n" +
                        "CSeq: 2\r\n" +
                        "User-Agent: CamScanIP-Mobile/2.0\r\n" +
                        authHeader +
                        "\r\n"
                out.write(optionsRequest.toByteArray(Charsets.UTF_8))
                out.flush()

                line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) break
                    if (trimmed.startsWith("RTSP/1.0") || trimmed.startsWith("RTSP/2.0")) {
                        val parts = trimmed.split(" ")
                        if (parts.size >= 2) statusCode = parts[1].toIntOrNull()
                    }
                    line = reader.readLine()
                }
            }

            val latency = System.currentTimeMillis() - startTime

            when (statusCode) {
                200 -> {
                    RtspTestResult(
                        isSuccess = true,
                        statusCode = 200,
                        statusDescription = "Stream RTSP ativo e validado (200 OK)",
                        serverHeader = serverHeader ?: "Servidor RTSP",
                        publicMethods = publicMethods,
                        responseTimeMs = latency,
                        testedUrl = fullUrl,
                        requiresAuth = false,
                        rawResponse = responseLines.joinToString("\n")
                    )
                }
                401 -> {
                    RtspTestResult(
                        isSuccess = true, // Câmera respondeu ao stream! Apenas requer a senha NVR correta
                        statusCode = 401,
                        statusDescription = "Câmera encontrada! Requer senha NVR correta (401 Unauthorized)",
                        serverHeader = serverHeader,
                        publicMethods = publicMethods,
                        responseTimeMs = latency,
                        testedUrl = fullUrl,
                        requiresAuth = true,
                        rawResponse = responseLines.joinToString("\n")
                    )
                }
                404 -> {
                    RtspTestResult(
                        isSuccess = false,
                        statusCode = 404,
                        statusDescription = "Porta RTSP ativa, mas caminho '$cleanPath' não existe na câmera (404)",
                        serverHeader = serverHeader,
                        responseTimeMs = latency,
                        testedUrl = fullUrl,
                        requiresAuth = false,
                        rawResponse = responseLines.joinToString("\n")
                    )
                }
                null -> {
                    RtspTestResult(
                        isSuccess = true,
                        statusCode = 200,
                        statusDescription = "Porta $port conectada com sucesso",
                        serverHeader = "Servidor RTSP",
                        responseTimeMs = latency,
                        testedUrl = fullUrl,
                        requiresAuth = false
                    )
                }
                else -> {
                    RtspTestResult(
                        isSuccess = false,
                        statusCode = statusCode,
                        statusDescription = "Resposta do servidor RTSP: $statusCode ($statusLine)",
                        serverHeader = serverHeader,
                        responseTimeMs = latency,
                        testedUrl = fullUrl,
                        requiresAuth = false,
                        rawResponse = responseLines.joinToString("\n")
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            RtspTestResult(
                isSuccess = false,
                statusCode = null,
                statusDescription = "Falha ao conectar em $ip:$port (${e.localizedMessage ?: "Timeout"})",
                responseTimeMs = latency,
                testedUrl = fullUrl,
                requiresAuth = false,
                rawResponse = e.toString()
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    data class PathProbeResult(
        val path: String,
        val label: String,
        val statusCode: Int?,
        val isWorking: Boolean,
        val statusMessage: String
    )

    /**
     * Testa em lote todos os caminhos RTSP comuns em câmeras Yoosee e IP para descobrir automaticamente o caminho correto!
     */
    suspend fun probeAllCommonPaths(
        ip: String,
        port: Int = 554,
        user: String = "admin",
        pass: String = ""
    ): List<PathProbeResult> = withContext(Dispatchers.IO) {
        val pathsToTest = listOf(
            "/onvif1" to "Yoosee HD (Stream 1)",
            "/onvif2" to "Yoosee SD (Stream 2)",
            "/live/ch0" to "Yoosee / Intelbras / Dahua",
            "/ch0_0.h264" to "Yoosee Direto",
            "/11" to "Yoosee Clássica (Canal 11)",
            "/12" to "Yoosee Clássica (Canal 12)",
            "/profile1" to "ONVIF Profile 1",
            "/" to "Raiz (/)"
        )

        pathsToTest.map { (path, label) ->
            val res = validateRtspStream(ip, port, path, user, pass)
            val isWorking = res.statusCode == 200 || res.statusCode == 401
            PathProbeResult(
                path = path,
                label = label,
                statusCode = res.statusCode,
                isWorking = isWorking,
                statusMessage = when (res.statusCode) {
                    200 -> "✅ Funcionando (200 OK)"
                    401 -> "🔒 Caminho Válido! Requer Senha NVR (401)"
                    404 -> "❌ Não encontrado (404)"
                    null -> "⚠️ Sem resposta"
                    else -> "Código ${res.statusCode}"
                }
            )
        }
    }

    fun buildRtspUrl(
        ip: String,
        port: Int,
        path: String,
        user: String,
        pass: String
    ): String {
        val auth = if (user.isNotBlank()) {
            val encodedUser = Uri.encode(user)
            val encodedPass = if (pass.isNotBlank()) Uri.encode(pass) else ""
            if (encodedPass.isNotBlank()) "$encodedUser:$encodedPass@" else "$encodedUser@"
        } else ""
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "rtsp://$auth$ip:$port$cleanPath"
    }

    /**
     * Caminhos conhecidos de stream RTSP para marcas populares, incluindo Yoosee
     */
    val COMMON_STREAM_PATHS = listOf(
        "/onvif1" to "Yoosee HD (Stream 1)",
        "/onvif2" to "Yoosee SD (Stream 2 - Mais Leve)",
        "/live/ch0" to "Yoosee / Intelbras / Dahua",
        "/ch0_0.h264" to "Yoosee RTSP Direto",
        "/11" to "Yoosee Clássico (11)",
        "/12" to "Yoosee Clássico (12)",
        "/Streaming/Channels/101" to "Hikvision Principal",
        "/stream1" to "TP-Link Tapo HD"
    )
}
