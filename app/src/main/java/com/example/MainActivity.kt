package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.CameraFormDialog
import com.example.ui.components.LiveStreamPlayerDialog
import com.example.ui.components.YooseeDiagnosticDialog
import com.example.ui.screens.RegisteredCamerasScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val wifiInfo by viewModel.wifiInfo.collectAsStateWithLifecycle()
    val savedCameras by viewModel.savedCameras.collectAsStateWithLifecycle()
    val filterCurrentNetworkOnly by viewModel.filterCurrentNetworkOnly.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val cameraToWatch by viewModel.cameraToWatch.collectAsStateWithLifecycle()
    val isDialogOpen by viewModel.isDialogOpen.collectAsStateWithLifecycle()
    val dialogCameraToEdit by viewModel.dialogCameraToEdit.collectAsStateWithLifecycle()
    val isTestingRtsp by viewModel.isTestingRtsp.collectAsStateWithLifecycle()
    val lastRtspResult by viewModel.lastRtspResult.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isYooseeHelpOpen by viewModel.isYooseeHelpOpen.collectAsStateWithLifecycle()

    // Permissão de Localização para ler SSID/BSSID de Wi-Fi no Android 8.1+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshWifiInfo()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    val savedIpSet = remember(savedCameras) {
        savedCameras.map { it.ipLocal }.toSet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CamScan IP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (wifiInfo.isConnected) SuccessGreen else MaterialTheme.colorScheme.error)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (wifiInfo.isConnected) wifiInfo.ssid else "Sem Wi-Fi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openYooseeHelp() },
                        modifier = Modifier.testTag("action_yoosee_help")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Ajuda Câmera Yoosee",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.openManualAddDialog() },
                        modifier = Modifier.testTag("action_add_manual")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Adicionar Câmera Manualmente",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        BadgedBox(badge = {
                            if (savedCameras.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("${savedCameras.size}")
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Câmeras Cadastradas"
                            )
                        }
                    },
                    label = { Text("Câmeras") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_tab_cameras")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(badge = {
                            if (discoveredDevices.isNotEmpty()) {
                                Badge(
                                    containerColor = SuccessGreen,
                                    contentColor = MaterialTheme.colorScheme.surface
                                ) {
                                    Text("${discoveredDevices.size}")
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "Varredura de Rede"
                            )
                        }
                    },
                    label = { Text("Varredura") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_tab_scanner")
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selectedTab,
            label = "ScreenTransition",
            modifier = Modifier.padding(innerPadding)
        ) { tabIndex ->
            when (tabIndex) {
                0 -> RegisteredCamerasScreen(
                    wifiInfo = wifiInfo,
                    savedCameras = savedCameras,
                    filterCurrentNetworkOnly = filterCurrentNetworkOnly,
                    onToggleFilter = { viewModel.toggleNetworkFilter() },
                    onRefreshWifi = { viewModel.refreshWifiInfo() },
                    onWatchCamera = { camera -> viewModel.openLivePlayer(camera) },
                    onTestRtsp = { camera ->
                        viewModel.openEditDialog(camera)
                        viewModel.testRtsp(
                            ip = camera.ipLocal,
                            port = camera.portaRtsp,
                            path = camera.streamPath,
                            user = camera.usuario,
                            pass = camera.senha
                        )
                    },
                    onEditCamera = { camera -> viewModel.openEditDialog(camera) },
                    onDeleteCamera = { camera -> viewModel.deleteCamera(camera) },
                    onCopiedUrl = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("URL RTSP copiada para a área de transferência!")
                        }
                    },
                    onNavigateToScanner = { selectedTab = 1 },
                    onAddManual = { viewModel.openManualAddDialog() }
                )

                1 -> ScannerScreen(
                    wifiInfo = wifiInfo,
                    scanProgress = scanProgress,
                    discoveredDevices = discoveredDevices,
                    savedIpSet = savedIpSet,
                    onStartScan = { viewModel.startUnifiedScan() },
                    onStopScan = { viewModel.stopScan() },
                    onAddDeviceClick = { device -> viewModel.openAddDialogFromDevice(device) },
                    onAddSampleDevice = { viewModel.addSampleCameraForTesting() },
                    onOpenYooseeHelp = { viewModel.openYooseeHelp() }
                )
            }
        }
    }

    // Player de Vídeo Ao Vivo em Tempo Real (RTSP)
    if (cameraToWatch != null) {
        LiveStreamPlayerDialog(
            camera = cameraToWatch!!,
            onDismiss = { viewModel.closeLivePlayer() }
        )
    }

    // Modal / Dialog Formulário de Cadastro e Edição (Tela 3)
    if (isDialogOpen && dialogCameraToEdit != null) {
        CameraFormDialog(
            initialCamera = dialogCameraToEdit!!,
            isTestingRtsp = isTestingRtsp,
            rtspTestResult = lastRtspResult,
            onTestRtsp = { ip, port, path, user, pass ->
                viewModel.testRtsp(ip, port, path, user, pass)
            },
            onSave = { camera -> viewModel.saveCamera(camera) },
            onDismiss = { viewModel.closeDialog() }
        )
    }

    // Modal / Dialog de Diagnóstico e Guia Yoosee
    if (isYooseeHelpOpen) {
        val suggestedIp = if (wifiInfo.deviceIp != "0.0.0.0") {
            wifiInfo.deviceIp.substringBeforeLast('.') + ".105"
        } else {
            "192.168.1.105"
        }

        YooseeDiagnosticDialog(
            currentWifiBssid = wifiInfo.bssid,
            suggestedIp = suggestedIp,
            onSaveCamera = { camera -> viewModel.saveCamera(camera) },
            onDismiss = { viewModel.closeYooseeHelp() }
        )
    }
}
