package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.local.CameraEntity
import com.example.network.RtspValidator
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch

enum class VideoPlayerEngine {
    EXOPLAYER_TCP,
    EXOPLAYER_UDP,
    NATIVE_VIDEO_VIEW
}

@OptIn(UnstableApi::class)
@Composable
fun LiveStreamPlayerDialog(
    camera: CameraEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentPath by remember(camera) {
        mutableStateOf(camera.streamPath.ifBlank { "/onvif1" })
    }
    var currentPassword by remember(camera) { mutableStateOf(camera.senha) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var playerEngine by remember { mutableStateOf(VideoPlayerEngine.EXOPLAYER_TCP) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var reconnectTrigger by remember { mutableIntStateOf(0) }

    // Provedor automático de caminhos
    var isProbingPaths by remember { mutableStateOf(false) }
    var probeResults by remember { mutableStateOf<List<RtspValidator.PathProbeResult>?>(null) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    val cleanPath = if (currentPath.startsWith("/")) currentPath else "/$currentPath"

    val fullRtspUrl = remember(camera.ipLocal, camera.portaRtsp, cleanPath, camera.usuario, currentPassword) {
        RtspValidator.buildRtspUrl(
            ip = camera.ipLocal,
            port = camera.portaRtsp,
            path = cleanPath,
            user = camera.usuario,
            pass = currentPassword
        )
    }

    // Instância do ExoPlayer quando selecionado
    val exoPlayer = remember(context, reconnectTrigger, fullRtspUrl, playerEngine) {
        if (playerEngine == VideoPlayerEngine.NATIVE_VIDEO_VIEW) null
        else {
            ExoPlayer.Builder(context).build().apply {
                volume = if (isMuted) 0f else 1f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                isBuffering = true
                                playbackError = null
                            }
                            Player.STATE_READY -> {
                                isBuffering = false
                                isPlaying = true
                                playbackError = null
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                                isPlaying = false
                            }
                            Player.STATE_IDLE -> {
                                isBuffering = false
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        isPlaying = false
                        val errorMsg = error.localizedMessage ?: "Erro na conexão RTSP"

                        // Se falhou no modo TCP, sugere alternar para UDP
                        playbackError = when {
                            errorMsg.contains("401", ignoreCase = true) || errorMsg.contains("Unauthorized", ignoreCase = true) ->
                                "Autenticação recusada (401). A senha do NVR da Yoosee está incorreta ou precisa ser redefinida no app Yoosee."
                            errorMsg.contains("404", ignoreCase = true) ->
                                "Caminho $cleanPath não existe nesta câmera (404). Clique em 'Detectar Caminhos' abaixo para encontrar o correto."
                            errorMsg.contains("461", ignoreCase = true) || errorMsg.contains("transport", ignoreCase = true) ->
                                "Câmera não suporta transporte TCP. Alterne para o modo UDP no seletor abaixo."
                            else ->
                                "Não foi possível conectar: $errorMsg. Tente alternar o Stream (/onvif2), o Transporte (UDP) ou use o VLC."
                        }
                    }
                })

                try {
                    val mediaItem = MediaItem.fromUri(Uri.parse(fullRtspUrl))
                    val useTcp = (playerEngine == VideoPlayerEngine.EXOPLAYER_TCP)
                    val mediaSource = RtspMediaSource.Factory()
                        .setForceUseRtpTcp(useTcp)
                        .setTimeoutMs(9000)
                        .createMediaSource(mediaItem)

                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                } catch (e: Exception) {
                    playbackError = "Falha ao inicializar stream: ${e.localizedMessage}"
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.stop()
            exoPlayer?.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("live_stream_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header com Badge "AO VIVO"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) SuccessGreen else if (isBuffering) WarningOrange else ErrorRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = camera.nome,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isPlaying) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isPlaying) "AO VIVO" else if (isBuffering) "CONECTANDO..." else "ERRO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPlaying) SuccessGreen else if (isBuffering) WarningOrange else ErrorRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${camera.ipLocal}:${camera.portaRtsp} • $cleanPath • ${camera.marcaModelo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_stream_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Área do Player de Vídeo (Aspect Ratio 16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (playerEngine == VideoPlayerEngine.NATIVE_VIDEO_VIEW) {
                        // Modo VideoView Nativo do Android
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.parse(fullRtspUrl))
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnPreparedListener {
                                        isBuffering = false
                                        isPlaying = true
                                        playbackError = null
                                        start()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        isBuffering = false
                                        isPlaying = false
                                        playbackError = "Player nativo retornou erro ($what, $extra). Tente o modo ExoPlayer ou abra no VLC."
                                        true
                                    }
                                }
                            },
                            update = { view ->
                                view.setVideoURI(Uri.parse(fullRtspUrl))
                                view.start()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (exoPlayer != null) {
                        // Modo ExoPlayer (TCP ou UDP)
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    setShowNextButton(false)
                                    setShowPreviousButton(false)
                                    setShowFastForwardButton(false)
                                    setShowRewindButton(false)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Overlay de Carregamento
                    if (isBuffering && playbackError == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Conectando ao stream RTSP (${playerEngine.name.substringAfter('_')})...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }

                    // Overlay de Erro com Ações de Correção
                    if (playbackError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.88f),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = playbackError ?: "Erro ao carregar vídeo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Alternar Transporte (TCP <-> UDP)
                                    OutlinedButton(
                                        onClick = {
                                            playerEngine = if (playerEngine == VideoPlayerEngine.EXOPLAYER_TCP)
                                                VideoPlayerEngine.EXOPLAYER_UDP
                                            else
                                                VideoPlayerEngine.EXOPLAYER_TCP
                                            reconnectTrigger++
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (playerEngine == VideoPlayerEngine.EXOPLAYER_TCP) "Tentar UDP" else "Tentar TCP")
                                    }

                                    // Abrir no VLC (100% de compatibilidade)
                                    Button(
                                        onClick = { openExternalPlayer(context, fullRtspUrl) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Abrir no VLC")
                                    }
                                }
                            }
                        }
                    }
                }

                // Painel de Configurações e Diagnóstico de Stream
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Seletores de Canais Populares da Yoosee
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Canal de Stream:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = currentPath == "/onvif1",
                                onClick = { currentPath = "/onvif1"; reconnectTrigger++ },
                                label = { Text("/onvif1 (HD)") },
                                leadingIcon = { Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )

                            FilterChip(
                                selected = currentPath == "/onvif2",
                                onClick = { currentPath = "/onvif2"; reconnectTrigger++ },
                                label = { Text("/onvif2 (SD)") },
                                leadingIcon = { Icon(Icons.Default.Sd, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )

                            FilterChip(
                                selected = currentPath == "/live/ch0",
                                onClick = { currentPath = "/live/ch0"; reconnectTrigger++ },
                                label = { Text("/live/ch0") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )

                            FilterChip(
                                selected = currentPath == "/ch0_0.h264",
                                onClick = { currentPath = "/ch0_0.h264"; reconnectTrigger++ },
                                label = { Text("/ch0_0.h264") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Seleção de Engine / Transporte
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modo de Conexão:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = playerEngine == VideoPlayerEngine.EXOPLAYER_TCP,
                                onClick = { playerEngine = VideoPlayerEngine.EXOPLAYER_TCP; reconnectTrigger++ },
                                label = { Text("TCP (RTSP)") }
                            )

                            FilterChip(
                                selected = playerEngine == VideoPlayerEngine.EXOPLAYER_UDP,
                                onClick = { playerEngine = VideoPlayerEngine.EXOPLAYER_UDP; reconnectTrigger++ },
                                label = { Text("UDP") }
                            )

                            FilterChip(
                                selected = playerEngine == VideoPlayerEngine.NATIVE_VIDEO_VIEW,
                                onClick = { playerEngine = VideoPlayerEngine.NATIVE_VIDEO_VIEW; reconnectTrigger++ },
                                label = { Text("Nativo (VideoView)") }
                            )
                        }
                    }

                    // Ferramenta Mágica: "Detectar Caminhos de Vídeo da Câmera Automaticamente"
                    OutlinedButton(
                        onClick = {
                            isProbingPaths = true
                            probeResults = null
                            coroutineScope.launch {
                                val results = RtspValidator.probeAllCommonPaths(
                                    ip = camera.ipLocal,
                                    port = camera.portaRtsp,
                                    user = camera.usuario,
                                    pass = currentPassword
                                )
                                probeResults = results
                                isProbingPaths = false

                                // Se encontrar um caminho ativo (200 OK ou 401), seleciona ele automaticamente!
                                val bestPath = results.firstOrNull { it.statusCode == 200 }
                                    ?: results.firstOrNull { it.statusCode == 401 }
                                if (bestPath != null) {
                                    currentPath = bestPath.path
                                    reconnectTrigger++
                                }
                            }
                        },
                        enabled = !isProbingPaths,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("probe_paths_button")
                    ) {
                        if (isProbingPaths) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testando todos os caminhos RTSP...")
                        } else {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auto-Detectar Caminho de Vídeo da Yoosee")
                        }
                    }

                    // Exibição dos caminhos encontrados no auto-detect
                    if (probeResults != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Resultado da detecção de caminhos:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                probeResults!!.forEach { p ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                currentPath = p.path
                                                reconnectTrigger++
                                            },
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = "${p.path} (${p.label})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (currentPath == p.path) FontWeight.Bold else FontWeight.Normal,
                                                color = if (p.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = p.statusMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (p.statusCode == 200) SuccessGreen else if (p.statusCode == 401) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Campo para Atualizar/Corrigir a Senha do NVR diretamente aqui
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showSettingsPanel = !showSettingsPanel }) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showSettingsPanel) "Ocultar Senha NVR" else "Editar Senha NVR da Câmera")
                        }

                        IconButton(onClick = { reconnectTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reconectar")
                        }
                    }

                    if (showSettingsPanel) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = { currentPassword = it },
                                label = { Text("Senha do NVR (definida no app Yoosee)") },
                                placeholder = { Text("Ex: 123456 ou admin123") },
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )

                            Button(
                                onClick = { reconnectTrigger++ },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Aplicar")
                            }
                        }
                    }

                    // Botão Principal: Abrir no VLC / Player Externo
                    Button(
                        onClick = { openExternalPlayer(context, fullRtspUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_vlc_prominent_button")
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir Stream no VLC / Player Externo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun openExternalPlayer(context: Context, rtspUrl: String) {
    try {
        val uri = Uri.parse(rtspUrl)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/rtsp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(rtspUrl), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Para assistir com máxima compatibilidade, instale o aplicativo gratuito 'VLC for Android' na Play Store.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
