package com.example.network

import com.example.network.model.DiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PortScanner {

    // Portas reais e exclusivas de CÂMERAS DE VÍDEO (RTSP / ONVIF / Yoosee / Xiongmai / Hikvision)
    // NOTA: Portas 80 e 8080 foram removidas da busca principal para evitar falsos positivos
    // com roteadores Wi-Fi, Smart TVs, impressoras e computadores da rede!
    val CAMERA_CORE_PORTS = listOf(554, 5000, 8899, 8000, 34567)

    private const val SOCKET_TIMEOUT_MS = 400
    private const val MAX_CONCURRENT_PROBES = 32

    /**
     * Varre a sub-rede procurando câmeras através de portas RTSP/ONVIF conhecidas.
     * Ignora dispositivos que apenas possuem portas HTTP comuns (ex: roteadores e TVs).
     */
    suspend fun scanSubnetForRtsp(
        ipsToScan: List<String>,
        ports: List<Int> = CAMERA_CORE_PORTS,
        onProgress: (currentIp: String, scannedCount: Int, totalCount: Int) -> Unit,
        onDeviceFound: (DiscoveredDevice) -> Unit
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredDevice>()
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        val total = ipsToScan.size
        var scannedCount = 0

        coroutineScope {
            val tasks = ipsToScan.map { ip ->
                async {
                    semaphore.withPermit {
                        val result = checkIpPorts(ip, ports)

                        synchronized(this@PortScanner) {
                            scannedCount++
                            onProgress(ip, scannedCount, total)
                        }

                        if (result != null) {
                            synchronized(discovered) {
                                discovered.add(result)
                            }
                            onDeviceFound(result)
                        }
                    }
                }
            }
            tasks.awaitAll()
        }

        discovered
    }

    /**
     * Testa se um IP possui portas de vídeo/câmera abertas.
     * Retorna NULL se não possuir nenhuma porta de streaming real (eliminando falsos positivos).
     */
    suspend fun checkIpPorts(
        ip: String,
        ports: List<Int> = CAMERA_CORE_PORTS
    ): DiscoveredDevice? = withContext(Dispatchers.IO) {
        val openPorts = mutableListOf<Int>()
        var minLatency = Long.MAX_VALUE

        for (port in ports) {
            val tStart = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS)
                val latency = System.currentTimeMillis() - tStart
                openPorts.add(port)
                if (latency < minLatency) minLatency = latency
            } catch (_: Exception) {
                // Porta fechada ou timeout
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }

        // Filtro Rigoroso Anti-Falsos-Positivos:
        // Só é considerado câmera se tiver PELO MENOS uma porta específica de CFTV/streaming
        val hasRtsp = openPorts.contains(554)
        val hasYooseeMedia = openPorts.contains(5000) || openPorts.contains(8899)
        val hasHikvision = openPorts.contains(8000)
        val hasXm = openPorts.contains(34567)

        val isRealCamera = hasRtsp || hasYooseeMedia || hasHikvision || hasXm

        if (isRealCamera) {
            val (mfr, defPath, discType) = when {
                hasYooseeMedia -> Triple("Yoosee / Gwelltimes", "/onvif1", "YOOSEE_PORT")
                hasXm -> Triple("Xiongmai / ICSee / Yoosee", "/live/ch0", "XM_PORT")
                hasHikvision -> Triple("Hikvision / NVR", "/Streaming/Channels/101", "TCP_8000")
                hasRtsp -> Triple("Câmera RTSP (Porta 554)", "/onvif1", "TCP_554")
                else -> Triple("Câmera IP", "/onvif1", "TCP_CUSTOM")
            }

            DiscoveredDevice(
                ip = ip,
                hostname = null,
                openPorts = openPorts,
                isRtspOpen = hasRtsp,
                isOnvif = hasYooseeMedia || openPorts.contains(8899),
                manufacturer = mfr,
                model = null,
                name = if (hasYooseeMedia) "Câmera Yoosee ($ip)" else "Câmera IP ($ip)",
                responseTimeMs = if (minLatency != Long.MAX_VALUE) minLatency else 40,
                discoveryType = discType,
                defaultStreamPath = defPath
            )
        } else {
            // Desconsidera dispositivos genéricos (roteador, smart TV, etc.)
            null
        }
    }

    data class PortDiagnostic(
        val port: Int,
        val serviceName: String,
        val isOpen: Boolean,
        val latencyMs: Long?
    )

    /**
     * Diagnóstico aprofundado para um IP específico (ex: quando o usuário digita o IP da Yoosee)
     */
    suspend fun diagnoseIp(ip: String): List<PortDiagnostic> = withContext(Dispatchers.IO) {
        val targets = listOf(
            554 to "RTSP (Streaming de Vídeo)",
            5000 to "Yoosee Media / ONVIF",
            8899 to "ONVIF Alternativo / Yoosee",
            8000 to "DVR / NVR / Hikvision",
            34567 to "Xiongmai / ICSee / NetSurveillance",
            80 to "HTTP Web (Configuração)",
            8080 to "Web Alternativo"
        )

        targets.map { (port, service) ->
            val tStart = System.currentTimeMillis()
            var socket: Socket? = null
            var open = false
            var latency: Long? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 600)
                open = true
                latency = System.currentTimeMillis() - tStart
            } catch (_: Exception) {
                open = false
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
            PortDiagnostic(port, service, open, latency)
        }
    }
}
