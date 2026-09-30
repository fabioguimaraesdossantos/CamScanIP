package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.network.model.WifiNetworkInfo
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object WifiHelper {

    /**
     * Obtém informações detalhadas da conexão Wi-Fi atual e faixa de sub-rede
     */
    fun getWifiNetworkInfo(context: Context): WifiNetworkInfo {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifi) {
            // Verifica se há alguma interface wlan ativa mesmo que o transporte reportado seja diferente
            val wlanIp = getWlanIpv4Address()
            if (wlanIp != null) {
                val subnet = calculateSubnet(wlanIp.address, wlanIp.prefixLength)
                return WifiNetworkInfo(
                    isConnected = true,
                    ssid = "Rede Local (Wi-Fi)",
                    bssid = "00:00:00:00:00:00",
                    deviceIp = wlanIp.address.hostAddress ?: "0.0.0.0",
                    subnetMask = prefixToSubnetMask(wlanIp.prefixLength),
                    subnetCidr = subnet.cidr,
                    ipListToScan = subnet.hostsToScan,
                    networkInterfaceName = wlanIp.interfaceName
                )
            }

            return WifiNetworkInfo(
                isConnected = false,
                ssid = "Sem conexão Wi-Fi",
                bssid = "",
                deviceIp = "0.0.0.0",
                subnetMask = "255.255.255.0",
                subnetCidr = "192.168.1.0/24",
                ipListToScan = generateSubnetIps("192.168.1", 1, 254)
            )
        }

        // Wi-Fi está conectado
        var ssid = "Wi-Fi Conectado"
        var bssid = ""
        var linkSpeed = 0
        var frequency = 0

        try {
            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            if (wifiInfo != null) {
                linkSpeed = wifiInfo.linkSpeed
                frequency = wifiInfo.frequency
                val rawSsid = wifiInfo.ssid
                if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                    ssid = rawSsid.trim('"')
                }
                val rawBssid = wifiInfo.bssid
                if (!rawBssid.isNullOrBlank() && rawBssid != "02:00:00:00:00:00") {
                    bssid = rawBssid
                }
            }
        } catch (_: SecurityException) {
            // Em Android 8.1+, requer permissão de localização para ler SSID/BSSID
        }

        // Obter IP e prefixo da sub-rede
        val wlanIp = getWlanIpv4Address()
        val deviceIp = wlanIp?.address?.hostAddress ?: getDeviceIpFromWifiManager(wifiManager) ?: "192.168.1.100"
        val prefix = wlanIp?.prefixLength ?: 24
        val subnet = calculateSubnet(
            wlanIp?.address ?: InetAddress.getByName(deviceIp),
            prefix
        )

        return WifiNetworkInfo(
            isConnected = true,
            ssid = ssid,
            bssid = bssid.ifBlank { "wifi_${deviceIp.substringBeforeLast('.')}" },
            deviceIp = deviceIp,
            subnetMask = prefixToSubnetMask(prefix),
            subnetCidr = subnet.cidr,
            ipListToScan = subnet.hostsToScan,
            linkSpeedMbps = linkSpeed,
            frequencyMhz = frequency,
            networkInterfaceName = wlanIp?.interfaceName ?: "wlan0"
        )
    }

    private data class InterfaceAddressInfo(
        val address: InetAddress,
        val prefixLength: Int,
        val interfaceName: String
    )

    private fun getWlanIpv4Address(): InterfaceAddressInfo? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                // Prefer wlan0, ap0, etc.
                val name = intf.name.lowercase()
                val isWireless = name.contains("wlan") || name.contains("eth") || name.contains("ap")
                for (addr in intf.interfaceAddresses) {
                    val inetAddress = addr.address
                    if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress) {
                        if (isWireless || !name.contains("dummy")) {
                            return InterfaceAddressInfo(
                                address = inetAddress,
                                prefixLength = addr.networkPrefixLength.toInt().coerceIn(8, 30),
                                interfaceName = intf.name
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun getDeviceIpFromWifiManager(wifiManager: WifiManager?): String? {
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: return null
        if (ipInt == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            (ipInt shr 8) and 0xff,
            (ipInt shr 16) and 0xff,
            (ipInt shr 24) and 0xff
        )
    }

    data class SubnetCalculation(
        val cidr: String,
        val hostsToScan: List<String>
    )

    fun calculateSubnet(address: InetAddress, prefixLength: Int): SubnetCalculation {
        val ipBytes = address.address
        if (ipBytes.size != 4) {
            return SubnetCalculation("192.168.1.0/24", generateSubnetIps("192.168.1", 1, 254))
        }

        val ipInt = ((ipBytes[0].toInt() and 0xFF) shl 24) or
                ((ipBytes[1].toInt() and 0xFF) shl 16) or
                ((ipBytes[2].toInt() and 0xFF) shl 8) or
                (ipBytes[3].toInt() and 0xFF)

        // Limita a varredura a um bloco /24 para não congelar o dispositivo móvel se o roteador tiver /16 ou /8
        val effectivePrefix = prefixLength.coerceAtLeast(24)
        val mask = (-1 shl (32 - effectivePrefix))
        val networkInt = ipInt and mask

        val b1 = (networkInt ushr 24) and 0xFF
        val b2 = (networkInt ushr 16) and 0xFF
        val b3 = (networkInt ushr 8) and 0xFF

        val prefixBase = "$b1.$b2.$b3"
        val cidr = "$prefixBase.0/$effectivePrefix"

        val hosts = mutableListOf<String>()
        // Adiciona hosts .1 até .254
        for (i in 1..254) {
            hosts.add("$prefixBase.$i")
        }

        return SubnetCalculation(cidr, hosts)
    }

    private fun generateSubnetIps(base: String, start: Int, end: Int): List<String> {
        val list = ArrayList<String>(end - start + 1)
        for (i in start..end) {
            list.add("$base.$i")
        }
        return list
    }

    private fun prefixToSubnetMask(prefix: Int): String {
        val shift = (32 - prefix).coerceIn(0, 32)
        val mask = if (shift == 32) 0 else (-1 shl shift)
        return String.format(
            "%d.%d.%d.%d",
            (mask ushr 24) and 0xFF,
            (mask ushr 16) and 0xFF,
            (mask ushr 8) and 0xFF,
            mask and 0xFF
        )
    }
}
