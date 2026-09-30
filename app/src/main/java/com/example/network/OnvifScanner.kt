package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.network.model.DiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.regex.Pattern

object OnvifScanner {

    private const val ONVIF_MULTICAST_IP = "239.255.255.250"
    private const val BROADCAST_IP = "255.255.255.255"
    private const val ONVIF_PORT = 3702
    private const val YOOSEE_DISCOVERY_PORT = 5000
    private const val BUFFER_SIZE = 8192

    /**
     * Envia pacotes UDP Multicast e Broadcast (WS-Discovery + Yoosee) nas portas 3702 e 5000 e coleta respostas.
     */
    suspend fun discoverOnvifDevices(
        context: Context,
        timeoutMs: Int = 4000,
        onDeviceFound: (DiscoveredDevice) -> Unit
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val discoveredList = mutableListOf<DiscoveredDevice>()
        val foundIps = mutableSetOf<String>()

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("ONVIF_DISCOVERY_LOCK")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket()
            socket.soTimeout = 800
            socket.broadcast = true

            val multicastGroup = InetAddress.getByName(ONVIF_MULTICAST_IP)
            val broadcastGroup = InetAddress.getByName(BROADCAST_IP)

            // Probe 1: Padrão ONVIF NetworkVideoTransmitter
            val uuid1 = UUID.randomUUID().toString()
            val probeXml1 = buildOnvifProbeXml(uuid1, "dn:NetworkVideoTransmitter")
            val bytes1 = probeXml1.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(bytes1, bytes1.size, multicastGroup, ONVIF_PORT))
            socket.send(DatagramPacket(bytes1, bytes1.size, broadcastGroup, ONVIF_PORT))

            // Probe 2: Probe genérico (ampla compatibilidade)
            val uuid2 = UUID.randomUUID().toString()
            val probeXml2 = buildGenericWsDiscoveryXml(uuid2)
            val bytes2 = probeXml2.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(bytes2, bytes2.size, multicastGroup, ONVIF_PORT))
            socket.send(DatagramPacket(bytes2, bytes2.size, broadcastGroup, ONVIF_PORT))

            // Probe 3: Sonda para câmeras com descoberta na porta 5000 (comum em Yoosee/Gwell)
            try {
                socket.send(DatagramPacket(bytes2, bytes2.size, broadcastGroup, YOOSEE_DISCOVERY_PORT))
            } catch (_: Exception) {
            }

            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(BUFFER_SIZE)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val receivePacket = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(receivePacket)
                    val responseStr = String(
                        receivePacket.data,
                        receivePacket.offset,
                        receivePacket.length,
                        Charsets.UTF_8
                    )

                    val senderIp = receivePacket.address.hostAddress ?: continue
                    if (foundIps.contains(senderIp)) continue

                    val device = parseOnvifResponse(responseStr, senderIp)
                    foundIps.add(senderIp)
                    discoveredList.add(device)
                    onDeviceFound(device)
                } catch (_: SocketTimeoutException) {
                    // Continua até atingir timeoutMs
                } catch (e: IOException) {
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (_: Exception) {
            }
        }

        discoveredList
    }

    private fun buildOnvifProbeXml(uuid: String, type: String): String {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<Envelope xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" " +
                "xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\" " +
                "xmlns=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<Header>" +
                "<wsa:MessageID xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">uuid:$uuid</wsa:MessageID>" +
                "<wsa:To xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>" +
                "<wsa:Action xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>" +
                "</Header>" +
                "<Body>" +
                "<Probe xmlns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">" +
                "<Types>$type</Types>" +
                "</Probe>" +
                "</Body>" +
                "</Envelope>"
    }

    private fun buildGenericWsDiscoveryXml(uuid: String): String {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<Envelope xmlns=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<Header>" +
                "<wsa:MessageID xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">uuid:$uuid</wsa:MessageID>" +
                "<wsa:To xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>" +
                "<wsa:Action xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>" +
                "</Header>" +
                "<Body>" +
                "<Probe xmlns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" />" +
                "</Body>" +
                "</Envelope>"
    }

    private fun parseOnvifResponse(xml: String, fallbackIp: String): DiscoveredDevice {
        val xaddrsPattern = Pattern.compile("(?i)<[^:]*:?XAddrs>([^<]+)</[^:]*:?XAddrs>")
        val xaddrsMatcher = xaddrsPattern.matcher(xml)
        var serviceUrl: String? = null
        var ip = fallbackIp
        val detectedPorts = mutableSetOf(554)

        if (xaddrsMatcher.find()) {
            val fullUrl = xaddrsMatcher.group(1)?.trim()?.split(" ")?.firstOrNull()
            serviceUrl = fullUrl
            if (fullUrl != null) {
                val ipMatch = Pattern.compile("https?://([0-9.]+)(?::(\\d+))?").matcher(fullUrl)
                if (ipMatch.find()) {
                    val foundIp = ipMatch.group(1)
                    if (!foundIp.isNullOrBlank()) ip = foundIp
                    val portStr = ipMatch.group(2)
                    if (!portStr.isNullOrBlank()) {
                        portStr.toIntOrNull()?.let { detectedPorts.add(it) }
                    }
                }
            }
        }

        val scopesPattern = Pattern.compile("(?i)<[^:]*:?Scopes>([^<]+)</[^:]*:?Scopes>")
        val scopesMatcher = scopesPattern.matcher(xml)
        val scopesList = mutableListOf<String>()
        var manufacturer: String? = null
        var model: String? = null
        var cameraName = "Câmera ONVIF ($ip)"

        if (scopesMatcher.find()) {
            val rawScopes = scopesMatcher.group(1)?.trim() ?: ""
            val items = rawScopes.split(Pattern.compile("\\s+"))
            for (scope in items) {
                if (scope.isNotBlank()) scopesList.add(scope)
                val lower = scope.lowercase()
                if (lower.contains("name/")) {
                    val namePart = scope.substringAfterLast("name/").replace("%20", " ")
                    if (namePart.isNotBlank()) cameraName = namePart
                } else if (lower.contains("hardware/")) {
                    model = scope.substringAfterLast("hardware/").replace("%20", " ")
                } else if (lower.contains("manufacturer/") || lower.contains("mfr/")) {
                    manufacturer = scope.substringAfterLast("/").replace("%20", " ")
                }
            }
        }

        // Reconhecimento de fabricantes, incluindo Yoosee / Gwelltimes
        val lowerXml = xml.lowercase()
        if (manufacturer == null) {
            when {
                lowerXml.contains("yoosee") || lowerXml.contains("gwell") || lowerXml.contains("2cu") -> manufacturer = "Yoosee / Gwell"
                lowerXml.contains("hikvision") -> manufacturer = "Hikvision"
                lowerXml.contains("dahua") -> manufacturer = "Dahua"
                lowerXml.contains("intelbras") -> manufacturer = "Intelbras"
                lowerXml.contains("axis") -> manufacturer = "Axis Communications"
                lowerXml.contains("foscam") -> manufacturer = "Foscam"
                lowerXml.contains("tp-link") || lowerXml.contains("tapo") -> manufacturer = "TP-Link Tapo"
                lowerXml.contains("reolink") -> manufacturer = "Reolink"
                lowerXml.contains("xiongmai") || lowerXml.contains("xm") -> manufacturer = "Xiongmai / ICSee"
                lowerXml.contains("vstarcam") -> manufacturer = "VStarcam"
                else -> manufacturer = "ONVIF Compatível"
            }
        }

        val isYoosee = manufacturer?.contains("Yoosee", ignoreCase = true) == true ||
                lowerXml.contains("yoosee") || lowerXml.contains("gwell")

        return DiscoveredDevice(
            ip = ip,
            hostname = null,
            openPorts = detectedPorts.toList(),
            isRtspOpen = true,
            isOnvif = true,
            onvifServiceUrl = serviceUrl,
            manufacturer = manufacturer,
            model = model,
            name = if (isYoosee) "Câmera Yoosee ($ip)" else cameraName,
            responseTimeMs = 25,
            discoveryType = if (isYoosee) "YOOSEE_ONVIF" else "ONVIF",
            defaultStreamPath = if (isYoosee) "/onvif1" else "/onvif1",
            scopes = scopesList
        )
    }
}
