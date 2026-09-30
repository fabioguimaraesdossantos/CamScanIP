package com.example.network.model

/**
 * Informações da rede Wi-Fi local à qual o dispositivo móvel está conectado.
 */
data class WifiNetworkInfo(
    val isConnected: Boolean = false,
    val ssid: String = "Desconectado",
    val bssid: String = "",
    val deviceIp: String = "0.0.0.0",
    val subnetMask: String = "255.255.255.0",
    val subnetCidr: String = "192.168.1.0/24",
    val ipListToScan: List<String> = emptyList(),
    val linkSpeedMbps: Int = 0,
    val frequencyMhz: Int = 0,
    val networkInterfaceName: String = "wlan0"
)

/**
 * Dispositivo / Câmera IP descoberto durante a varredura
 */
data class DiscoveredDevice(
    val ip: String,
    val hostname: String? = null,
    val openPorts: List<Int> = listOf(554),
    val isRtspOpen: Boolean = false,
    val isOnvif: Boolean = false,
    val onvifServiceUrl: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val name: String = "Câmera IP ($ip)",
    val responseTimeMs: Long = 0,
    val discoveryType: String = "ONVIF", // "ONVIF" | "TCP_554" | "HYBRID" | "YOOSEE_PORT"
    val defaultStreamPath: String = "/onvif1",
    val scopes: List<String> = emptyList()
)

/**
 * Resultado do teste de conexão e validação de stream RTSP
 */
data class RtspTestResult(
    val isSuccess: Boolean = false,
    val statusCode: Int? = null,
    val statusDescription: String = "",
    val serverHeader: String? = null,
    val publicMethods: String? = null,
    val responseTimeMs: Long = 0L,
    val testedUrl: String = "",
    val requiresAuth: Boolean = false,
    val rawResponse: String? = null
)

/**
 * Modo de varredura selecionável pelo usuário
 */
enum class ScanMode(val label: String, val description: String) {
    YOOSEE_DEEP("Varredura Yoosee & Câmeras Wi-Fi", "Varre portas Yoosee (554, 5000, 8899, 80, 8000, 34567) + Broadcast UDP"),
    HYBRID_ALL("Varredura Híbrida Completa", "Combina descoberta ONVIF e varredura TCP de portas RTSP"),
    ONVIF_MULTICAST("ONVIF Multicast (Porta 3702)", "Busca rápida padrão WS-Discovery via pacotes UDP Multicast"),
    TCP_PORT_554("Varredura RTSP TCP (Porta 554)", "Varre a sub-rede IP a IP verificando portas RTSP abertas")
}

/**
 * Estado do progresso de varredura da rede
 */
data class ScanProgress(
    val isScanning: Boolean = false,
    val mode: ScanMode = ScanMode.YOOSEE_DEEP,
    val currentIp: String = "",
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val devicesFoundCount: Int = 0,
    val statusMessage: String = "Pronto para iniciar a varredura"
) {
    val progressFraction: Float
        get() = if (totalCount > 0) (scannedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
}
