package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CameraEntity
import com.example.data.local.CameraRepository
import com.example.network.OnvifScanner
import com.example.network.PortScanner
import com.example.network.RtspValidator
import com.example.network.WifiHelper
import com.example.network.model.DiscoveredDevice
import com.example.network.model.RtspTestResult
import com.example.network.model.ScanMode
import com.example.network.model.ScanProgress
import com.example.network.model.WifiNetworkInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CameraRepository

    // Estado da rede Wi-Fi
    private val _wifiInfo = MutableStateFlow(WifiNetworkInfo())
    val wifiInfo: StateFlow<WifiNetworkInfo> = _wifiInfo.asStateFlow()

    // Filtro de lista
    private val _filterCurrentNetworkOnly = MutableStateFlow(false)
    val filterCurrentNetworkOnly: StateFlow<Boolean> = _filterCurrentNetworkOnly.asStateFlow()

    // Lista de câmeras do Room Database
    val savedCameras: StateFlow<List<CameraEntity>>

    // Estado da Varredura
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var scanJob: Job? = null

    // Player de Vídeo Ao Vivo
    private val _cameraToWatch = MutableStateFlow<CameraEntity?>(null)
    val cameraToWatch: StateFlow<CameraEntity?> = _cameraToWatch.asStateFlow()

    // Teste de RTSP em andamento
    private val _isTestingRtsp = MutableStateFlow(false)
    val isTestingRtsp: StateFlow<Boolean> = _isTestingRtsp.asStateFlow()

    private val _lastRtspResult = MutableStateFlow<RtspTestResult?>(null)
    val lastRtspResult: StateFlow<RtspTestResult?> = _lastRtspResult.asStateFlow()

    // Diálogo de Cadastro / Edição
    private val _dialogCameraToEdit = MutableStateFlow<CameraEntity?>(null)
    val dialogCameraToEdit: StateFlow<CameraEntity?> = _dialogCameraToEdit.asStateFlow()

    private val _isDialogOpen = MutableStateFlow(false)
    val isDialogOpen: StateFlow<Boolean> = _isDialogOpen.asStateFlow()

    // Diálogo de Ajuda e Diagnóstico Yoosee
    private val _isYooseeHelpOpen = MutableStateFlow(false)
    val isYooseeHelpOpen: StateFlow<Boolean> = _isYooseeHelpOpen.asStateFlow()

    // Mensagem de Feedback rápido (Snackbar / Toast)
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = CameraRepository(database.cameraDao())

        savedCameras = combine(
            repository.allCameras,
            _filterCurrentNetworkOnly,
            _wifiInfo
        ) { cameras, filterOnlyCurrent, wifi ->
            if (filterOnlyCurrent && wifi.bssid.isNotBlank()) {
                cameras.filter { it.bssidWifi == wifi.bssid }
            } else {
                cameras
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        refreshWifiInfo()
    }

    fun refreshWifiInfo() {
        val info = WifiHelper.getWifiNetworkInfo(getApplication())
        _wifiInfo.value = info
    }

    fun toggleNetworkFilter() {
        _filterCurrentNetworkOnly.value = !_filterCurrentNetworkOnly.value
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun openLivePlayer(camera: CameraEntity) {
        _cameraToWatch.value = camera
    }

    fun closeLivePlayer() {
        _cameraToWatch.value = null
    }

    fun openYooseeHelp() {
        _isYooseeHelpOpen.value = true
    }

    fun closeYooseeHelp() {
        _isYooseeHelpOpen.value = false
    }

    /**
     * Varredura Automática Completa Unificada:
     * Executa simultaneamente ONVIF Multicast (3702), Broadcast Yoosee (5000) e
     * varredura TCP em portas reais de câmeras de segurança (554, 5000, 8899, 8000, 34567),
     * descartando roteadores e computadores da rede para evitar falsos positivos!
     */
    fun startUnifiedScan() {
        scanJob?.cancel()
        refreshWifiInfo()

        val wifi = _wifiInfo.value
        val hostsToScan = wifi.ipListToScan

        _discoveredDevices.value = emptyList()
        _scanProgress.value = ScanProgress(
            isScanning = true,
            mode = ScanMode.HYBRID_ALL,
            currentIp = "239.255.255.250:3702",
            scannedCount = 0,
            totalCount = hostsToScan.size,
            devicesFoundCount = 0,
            statusMessage = "Transmitindo sondas ONVIF e Yoosee (UDP Multicast + Broadcast)..."
        )

        scanJob = viewModelScope.launch {
            val foundSet = mutableSetOf<String>()

            fun addDiscoveredDevice(device: DiscoveredDevice) {
                if (!foundSet.contains(device.ip)) {
                    foundSet.add(device.ip)
                    val updated = _discoveredDevices.value + device
                    _discoveredDevices.value = updated
                    _scanProgress.value = _scanProgress.value.copy(
                        devicesFoundCount = updated.size
                    )
                }
            }

            try {
                // Etapa 1: Envia sondas ONVIF e Yoosee UDP
                OnvifScanner.discoverOnvifDevices(
                    context = getApplication(),
                    timeoutMs = 3500,
                    onDeviceFound = { device ->
                        addDiscoveredDevice(device)
                    }
                )

                // Etapa 2: Varre portas exclusivas de câmeras de vídeo na sub-rede
                _scanProgress.value = _scanProgress.value.copy(
                    statusMessage = "Varrendo sub-rede ${wifi.subnetCidr} por portas de vídeo (554, 5000, 8899)..."
                )

                PortScanner.scanSubnetForRtsp(
                    ipsToScan = hostsToScan,
                    ports = PortScanner.CAMERA_CORE_PORTS,
                    onProgress = { currentIp, scanned, total ->
                        _scanProgress.value = _scanProgress.value.copy(
                            currentIp = currentIp,
                            scannedCount = scanned,
                            totalCount = total,
                            statusMessage = "Testando portas de CFTV em $currentIp ($scanned/$total)"
                        )
                    },
                    onDeviceFound = { device ->
                        addDiscoveredDevice(device)
                    }
                )

                val totalFound = _discoveredDevices.value.size
                _scanProgress.value = _scanProgress.value.copy(
                    isScanning = false,
                    statusMessage = if (totalFound > 0)
                        "Varredura concluída! $totalFound câmera(s) real(is) identificada(s)."
                    else
                        "Varredura concluída. Se a sua câmera Yoosee não apareceu, ative a 'Conexão NVR' no app Yoosee."
                )
            } catch (e: Exception) {
                _scanProgress.value = _scanProgress.value.copy(
                    isScanning = false,
                    statusMessage = "Varredura interrompida: ${e.localizedMessage ?: "Erro de rede"}"
                )
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _scanProgress.value = _scanProgress.value.copy(
            isScanning = false,
            statusMessage = "Varredura cancelada pelo usuário."
        )
    }

    fun addSampleCameraForTesting() {
        val sample = DiscoveredDevice(
            ip = "192.168.1.105",
            hostname = "yoosee-ipc",
            openPorts = listOf(554, 5000, 8899),
            isRtspOpen = true,
            isOnvif = true,
            onvifServiceUrl = "http://192.168.1.105:5000/onvif/device_service",
            manufacturer = "Yoosee IP Camera (Gwell)",
            model = "Yoosee 1080p Smart Camera",
            name = "Câmera Yoosee Wi-Fi (Sala)",
            responseTimeMs = 22,
            discoveryType = "YOOSEE_PORT",
            defaultStreamPath = "/onvif1"
        )
        val current = _discoveredDevices.value
        if (current.none { it.ip == sample.ip }) {
            _discoveredDevices.value = current + sample
            showMessage("Câmera Yoosee simulada adicionada à lista de descobertas")
        }
    }

    fun testRtsp(
        ip: String,
        port: Int,
        path: String,
        user: String,
        pass: String
    ) {
        viewModelScope.launch {
            _isTestingRtsp.value = true
            _lastRtspResult.value = null

            val result = RtspValidator.validateRtspStream(
                ip = ip,
                port = port,
                path = path,
                user = user,
                pass = pass
            )

            _lastRtspResult.value = result
            _isTestingRtsp.value = false
        }
    }

    fun clearRtspResult() {
        _lastRtspResult.value = null
    }

    fun openAddDialogFromDevice(device: DiscoveredDevice) {
        val currentWifi = _wifiInfo.value
        val entity = CameraEntity(
            id = 0,
            nome = device.name,
            ipLocal = device.ip,
            portaRtsp = if (device.openPorts.contains(554)) 554 else device.openPorts.firstOrNull() ?: 554,
            usuario = "admin",
            senha = "",
            bssidWifi = currentWifi.bssid,
            dataCadastro = System.currentTimeMillis(),
            streamPath = device.defaultStreamPath,
            tipoDescoberta = device.discoveryType,
            marcaModelo = device.manufacturer ?: "Câmera IP"
        )
        _dialogCameraToEdit.value = entity
        _lastRtspResult.value = null
        _isDialogOpen.value = true
    }

    fun openManualAddDialog() {
        val currentWifi = _wifiInfo.value
        val entity = CameraEntity(
            id = 0,
            nome = "Câmera Yoosee / IP",
            ipLocal = if (currentWifi.deviceIp != "0.0.0.0") currentWifi.deviceIp.substringBeforeLast('.') + ".105" else "192.168.1.105",
            portaRtsp = 554,
            usuario = "admin",
            senha = "",
            bssidWifi = currentWifi.bssid,
            dataCadastro = System.currentTimeMillis(),
            streamPath = "/onvif1",
            tipoDescoberta = "MANUAL",
            marcaModelo = "Yoosee / IP"
        )
        _dialogCameraToEdit.value = entity
        _lastRtspResult.value = null
        _isDialogOpen.value = true
    }

    fun openEditDialog(camera: CameraEntity) {
        _dialogCameraToEdit.value = camera
        _lastRtspResult.value = null
        _isDialogOpen.value = true
    }

    fun closeDialog() {
        _isDialogOpen.value = false
        _dialogCameraToEdit.value = null
        _lastRtspResult.value = null
    }

    fun saveCamera(camera: CameraEntity) {
        viewModelScope.launch {
            if (camera.id == 0L) {
                repository.insertCamera(camera)
                showMessage("Câmera \"${camera.nome}\" cadastrada com sucesso!")
            } else {
                repository.updateCamera(camera)
                showMessage("Câmera \"${camera.nome}\" atualizada com sucesso!")
            }
            closeDialog()
        }
    }

    fun deleteCamera(camera: CameraEntity) {
        viewModelScope.launch {
            repository.deleteCamera(camera)
            showMessage("Câmera \"${camera.nome}\" removida.")
        }
    }
}
