package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CameraEntity
import com.example.network.WifiHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CamScan IP", appName)
    }

    @Test
    fun `camera entity builds correct rtsp url`() {
        val camera = CameraEntity(
            id = 1,
            nome = "Câmera Teste",
            ipLocal = "192.168.1.50",
            portaRtsp = 554,
            usuario = "admin",
            senha = "123",
            bssidWifi = "00:11:22:33:44:55",
            streamPath = "/onvif1"
        )

        val unmasked = camera.buildRtspUrl(maskPassword = false)
        assertEquals("rtsp://admin:123@192.168.1.50:554/onvif1", unmasked)

        val masked = camera.buildRtspUrl(maskPassword = true)
        assertEquals("rtsp://admin:******@192.168.1.50:554/onvif1", masked)
    }

    @Test
    fun `calculateSubnet produces 254 hosts for slash 24`() {
        val address = InetAddress.getByName("192.168.1.42")
        val subnet = WifiHelper.calculateSubnet(address, 24)

        assertEquals("192.168.1.0/24", subnet.cidr)
        assertEquals(254, subnet.hostsToScan.size)
        assertEquals("192.168.1.1", subnet.hostsToScan.first())
        assertEquals("192.168.1.254", subnet.hostsToScan.last())
    }
}
