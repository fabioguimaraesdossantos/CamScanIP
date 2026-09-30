package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CameraEntity
import com.example.network.RtspValidator
import com.example.network.model.RtspTestResult

@Composable
fun CameraFormDialog(
    initialCamera: CameraEntity,
    isTestingRtsp: Boolean,
    rtspTestResult: RtspTestResult?,
    onTestRtsp: (ip: String, port: Int, path: String, user: String, pass: String) -> Unit,
    onSave: (CameraEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var nome by remember(initialCamera) { mutableStateOf(initialCamera.nome) }
    var ipLocal by remember(initialCamera) { mutableStateOf(initialCamera.ipLocal) }
    var portaRtspStr by remember(initialCamera) { mutableStateOf(initialCamera.portaRtsp.toString()) }
    var streamPath by remember(initialCamera) { mutableStateOf(initialCamera.streamPath) }
    var usuario by remember(initialCamera) { mutableStateOf(initialCamera.usuario.ifBlank { "admin" }) }
    var senha by remember(initialCamera) { mutableStateOf(initialCamera.senha) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val portaInt = portaRtspStr.toIntOrNull() ?: 554

    val isEditing = initialCamera.id != 0L

    // Preview em tempo real da URL RTSP
    val cleanPath = if (streamPath.startsWith("/")) streamPath else "/$streamPath"
    val authPart = if (usuario.isNotBlank()) {
        if (senha.isNotBlank()) "$usuario:******@" else "$usuario@"
    } else ""
    val previewUrl = "rtsp://$authPart$ipLocal:$portaInt$cleanPath"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .testTag("camera_form_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Cabeçalho
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEditing) "Editar Câmera IP" else "Cadastrar Câmera IP",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Configuração de rede e stream RTSP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar modal"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Conteúdo rolável do formulário
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Nome da Câmera
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        label = { Text("Nome da Câmera") },
                        placeholder = { Text("Ex: Câmera Garagem / Portaria") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_camera_name"),
                        leadingIcon = {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                        }
                    )

                    // Linha IP e Porta
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = ipLocal,
                            onValueChange = { ipLocal = it.trim() },
                            label = { Text("Endereço IP") },
                            placeholder = { Text("192.168.1.50") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(2f)
                                .testTag("input_camera_ip"),
                            leadingIcon = {
                                Icon(Icons.Default.Lan, contentDescription = null)
                            }
                        )

                        OutlinedTextField(
                            value = portaRtspStr,
                            onValueChange = { portaRtspStr = it.filter { char -> char.isDigit() } },
                            label = { Text("Porta RTSP") },
                            placeholder = { Text("554") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_camera_port"),
                            leadingIcon = {
                                Icon(Icons.Default.Pin, contentDescription = null)
                            }
                        )
                    }

                    // Caminho do Stream RTSP
                    OutlinedTextField(
                        value = streamPath,
                        onValueChange = { streamPath = it.trim() },
                        label = { Text("Caminho do Stream RTSP") },
                        placeholder = { Text("/onvif1 ou /live/ch0") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_camera_stream_path"),
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null)
                        }
                    )

                    // Chips de atalho para caminhos populares
                    Text(
                        text = "Atalhos de marcas e modelos comuns:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RtspValidator.COMMON_STREAM_PATHS.forEach { (path, label) ->
                            FilterChip(
                                selected = streamPath == path,
                                onClick = { streamPath = path },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Linha Usuário e Senha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = usuario,
                            onValueChange = { usuario = it },
                            label = { Text("Usuário") },
                            placeholder = { Text("admin") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_camera_user"),
                            leadingIcon = {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                            }
                        )

                        OutlinedTextField(
                            value = senha,
                            onValueChange = { senha = it },
                            label = { Text("Senha") },
                            placeholder = { Text("Senha da câmera") },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_camera_password"),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isPasswordVisible) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            }
                        )
                    }

                    // Card de Pré-visualização da URL RTSP
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "URL RTSP Gerada:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = previewUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2
                            )
                        }
                    }

                    // Botão para Testar Conexão RTSP em tempo real
                    OutlinedButton(
                        onClick = {
                            onTestRtsp(
                                ipLocal,
                                portaInt,
                                cleanPath,
                                usuario,
                                senha
                            )
                        },
                        enabled = ipLocal.isNotBlank() && !isTestingRtsp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_rtsp_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isTestingRtsp) "Testando Conexão..." else "Testar Conexão RTSP Agora")
                    }

                    // Banner de Feedback do teste RTSP
                    RtspTestResultBanner(
                        result = rtspTestResult,
                        isLoading = isTestingRtsp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rodapé de Ações
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_camera_button")
                    ) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val updated = initialCamera.copy(
                                nome = nome.ifBlank { "Câmera $ipLocal" },
                                ipLocal = ipLocal,
                                portaRtsp = portaInt,
                                streamPath = cleanPath,
                                usuario = usuario.ifBlank { "admin" },
                                senha = senha
                            )
                            onSave(updated)
                        },
                        enabled = ipLocal.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("save_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditing) "Atualizar Câmera" else "Salvar Câmera")
                    }
                }
            }
        }
    }
}
