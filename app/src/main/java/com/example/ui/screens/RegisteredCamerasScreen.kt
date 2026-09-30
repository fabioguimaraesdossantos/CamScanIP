package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.CameraEntity
import com.example.network.model.WifiNetworkInfo
import com.example.ui.components.CameraCard
import com.example.ui.components.WifiStatusCard

@Composable
fun RegisteredCamerasScreen(
    wifiInfo: WifiNetworkInfo,
    savedCameras: List<CameraEntity>,
    filterCurrentNetworkOnly: Boolean,
    onToggleFilter: () -> Unit,
    onRefreshWifi: () -> Unit,
    onWatchCamera: (CameraEntity) -> Unit,
    onTestRtsp: (CameraEntity) -> Unit,
    onEditCamera: (CameraEntity) -> Unit,
    onDeleteCamera: (CameraEntity) -> Unit,
    onCopiedUrl: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onAddManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("registered_cameras_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card de Status da Conexão Wi-Fi
            item {
                WifiStatusCard(
                    wifiInfo = wifiInfo,
                    onRefresh = onRefreshWifi
                )
            }

            // Barra de Filtros e Total de Câmeras
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Câmeras Cadastradas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${savedCameras.size} dispositivo(s) salvo(s) localmente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilterChip(
                        selected = filterCurrentNetworkOnly,
                        onClick = onToggleFilter,
                        label = {
                            Text(if (filterCurrentNetworkOnly) "Rede Atual" else "Todas as Redes")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("toggle_network_filter_chip")
                    )
                }
            }

            // Lista de Câmeras ou Empty State
            if (savedCameras.isEmpty()) {
                item {
                    EmptyCamerasState(
                        filterCurrentNetworkOnly = filterCurrentNetworkOnly,
                        onScanClick = onNavigateToScanner,
                        onAddManualClick = onAddManual
                    )
                }
            } else {
                items(
                    items = savedCameras,
                    key = { it.id }
                ) { camera ->
                    CameraCard(
                        camera = camera,
                        currentWifiBssid = wifiInfo.bssid,
                        onWatchStream = onWatchCamera,
                        onTestRtsp = onTestRtsp,
                        onEdit = onEditCamera,
                        onDelete = onDeleteCamera,
                        onCopiedUrl = onCopiedUrl
                    )
                }
            }
        }

        // Botão Flutuante para Adicionar / Escanear
        FloatingActionButton(
            onClick = onNavigateToScanner,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_scan_network")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = "Escanear Rede"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Escanear",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyCamerasState(
    filterCurrentNetworkOnly: Boolean,
    onScanClick: () -> Unit,
    onAddManualClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("empty_cameras_state"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (filterCurrentNetworkOnly)
                    "Nenhuma câmera cadastrada na rede atual"
                else
                    "Nenhuma câmera IP cadastrada",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Use o scanner automático para descobrir câmeras ONVIF e RTSP na sua rede Wi-Fi ou adicione manualmente.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_state_scan_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Escanear Wi-Fi")
                }

                OutlinedButton(
                    onClick = onAddManualClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_state_manual_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manual")
                }
            }
        }
    }
}
