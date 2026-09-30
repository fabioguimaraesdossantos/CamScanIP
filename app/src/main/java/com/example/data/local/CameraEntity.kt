package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidade Room para armazenamento de câmeras IP descobertas e cadastradas.
 * Atende aos campos solicitados:
 * id, nome, ip_local, porta_rtsp, usuario, senha, bssid_wifi, data_cadastro
 */
@Entity(tableName = "cameras")
data class CameraEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "nome")
    val nome: String,

    @ColumnInfo(name = "ip_local")
    val ipLocal: String,

    @ColumnInfo(name = "porta_rtsp")
    val portaRtsp: Int = 554,

    @ColumnInfo(name = "usuario")
    val usuario: String = "admin",

    @ColumnInfo(name = "senha")
    val senha: String = "",

    @ColumnInfo(name = "bssid_wifi")
    val bssidWifi: String = "",

    @ColumnInfo(name = "data_cadastro")
    val dataCadastro: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "stream_path")
    val streamPath: String = "/onvif1",

    @ColumnInfo(name = "tipo_descoberta")
    val tipoDescoberta: String = "ONVIF", // "ONVIF", "TCP_554", "MANUAL"

    @ColumnInfo(name = "marca_modelo")
    val marcaModelo: String = "Câmera IP Genérica"
) {
    /**
     * Gera a URL completa do stream RTSP com credenciais
     */
    fun buildRtspUrl(maskPassword: Boolean = false): String {
        val authPart = if (usuario.isNotBlank()) {
            val pass = if (maskPassword && senha.isNotEmpty()) "******" else senha
            if (pass.isNotEmpty()) "$usuario:$pass@" else "$usuario@"
        } else ""

        val path = if (streamPath.startsWith("/")) streamPath else "/$streamPath"
        return "rtsp://$authPart$ipLocal:$portaRtsp$path"
    }
}
