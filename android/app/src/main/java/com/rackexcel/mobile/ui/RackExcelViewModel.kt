package com.rackexcel.mobile.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rackexcel.mobile.RackPrompt
import com.rackexcel.mobile.alarm.AlarmDeviceIdentity
import com.rackexcel.mobile.alarm.AlarmDeviceIdentityResolver
import com.rackexcel.mobile.alarm.AlarmMatchConfig
import com.rackexcel.mobile.alarm.AlarmMatchPolicy
import com.rackexcel.mobile.alarm.AlarmMatchResult
import com.rackexcel.mobile.alarm.AlarmRecord
import com.rackexcel.mobile.alarm.AlarmReviewDecision
import com.rackexcel.mobile.alarm.AlarmReviewInput
import com.rackexcel.mobile.alarm.AlarmReviewStateMachine
import com.rackexcel.mobile.alarm.AlarmSettings
import com.rackexcel.mobile.alarm.AlarmSettingsDefaults
import com.rackexcel.mobile.alarm.AlarmSettingsStore
import com.rackexcel.mobile.alarm.AlarmSeverity
import com.rackexcel.mobile.alarm.AlarmSource
import com.rackexcel.mobile.alarm.AlarmSourceFactory
import com.rackexcel.mobile.alarm.AlarmSourceMode
import com.rackexcel.mobile.alarm.AlarmStatus
import com.rackexcel.mobile.alarm.AlarmUiEffect
import com.rackexcel.mobile.alarm.AlarmAssetWritebackPolicy
import com.rackexcel.mobile.alarm.AlarmFollowUpAction
import com.rackexcel.mobile.alarm.AlarmMatchMode
import com.rackexcel.mobile.alarm.MockAlarmSource
import com.rackexcel.mobile.alarm.ConfiguredNetworkManagerGateway
import com.rackexcel.mobile.alarm.NetworkGatewayConfig
import com.rackexcel.mobile.alarm.NetworkGatewayConfigStore
import com.rackexcel.mobile.excel.ExportFileName
import com.rackexcel.mobile.excel.ExportSummary
import com.rackexcel.mobile.excel.ExportTask
import com.rackexcel.mobile.excel.ExportHoldPolicy
import com.rackexcel.mobile.excel.ImageOutcome
import com.rackexcel.mobile.excel.ImageProcessingState
import com.rackexcel.mobile.excel.ReviewPolicy
import com.rackexcel.mobile.excel.RepairEvent
import com.rackexcel.mobile.excel.TaskEfficiencyMetrics
import com.rackexcel.mobile.excel.XlsxWriter
import com.rackexcel.mobile.image.ImageQualityResult
import com.rackexcel.mobile.image.ImagePreprocessor
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.model.RackOrdering
import com.rackexcel.mobile.model.RiskAnalysisEngine
import com.rackexcel.mobile.network.RetryExecutor
import com.rackexcel.mobile.network.ConnectionRetryPolicy
import com.rackexcel.mobile.network.VisionApiClient
import com.rackexcel.mobile.network.VisionApiException
import com.rackexcel.mobile.storage.AppSettings
import com.rackexcel.mobile.storage.AppSettingsStore
import com.rackexcel.mobile.storage.ConnectedComputerStore
import com.rackexcel.mobile.storage.DesktopDeliveryReceipt
import com.rackexcel.mobile.storage.FileResultStore
import com.rackexcel.mobile.storage.ModelConfig
import com.rackexcel.mobile.storage.ModelProfile
import com.rackexcel.mobile.storage.ModelProfileCollection
import com.rackexcel.mobile.storage.ModelProfilePolicy
import com.rackexcel.mobile.storage.PromptTemplate
import com.rackexcel.mobile.storage.PromptTemplateStore
import com.rackexcel.mobile.storage.SecureConfigStore
import com.rackexcel.mobile.storage.TaskHistoryItem
import com.rackexcel.mobile.storage.TaskHistoryStore
import com.rackexcel.mobile.storage.TaskHistoryText
import com.rackexcel.mobile.storage.TaskReviewSnapshot
import com.rackexcel.mobile.storage.ActiveTaskSession
import com.rackexcel.mobile.storage.TaskSessionImage
import com.rackexcel.mobile.storage.TaskSessionStore
import com.rackexcel.mobile.storage.TaskSessionRecoveryPolicy
import com.rackexcel.mobile.receiver.ConnectedComputer
import com.rackexcel.mobile.receiver.DesktopReceiverClient
import com.rackexcel.mobile.receiver.DesktopReceiverDiagnostics
import com.rackexcel.mobile.receiver.DesktopReceiverErrorType
import com.rackexcel.mobile.receiver.DesktopReceiverException
import com.rackexcel.mobile.receiver.ReceiverPairingUriParser
import com.rackexcel.mobile.receiver.ReceiverTransferReceipt
import com.rackexcel.mobile.receiver.ReceiverUploadProgress
import com.rackexcel.mobile.receiver.ReceiverUploadSource
import com.rackexcel.mobile.task.RackEditPolicy
import com.rackexcel.mobile.task.TaskArchiveStore
import com.rackexcel.mobile.task.TaskMergePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class SelectedImage(
    val uri: Uri,
    val name: String,
    val id: String = UUID.randomUUID().toString(),
    val quality: ImageQualityResult? = null,
    /** App-private review copy used when a picker URI is unavailable after restart. */
    val archivedPath: String? = null,
    /** Full-resolution app-private copy retained only when the user enables it. */
    val originalPath: String? = null,
)

enum class EngineBannerPhase { IDLE, CHECKING, READY, FAILED }

enum class ExportPhase {
    IDLE,
    WAITING_CONFIRMATION,
    WRITING,
    COMPLETED,
    FAILED,
}

data class EngineBanner(
    val phase: EngineBannerPhase = EngineBannerPhase.IDLE,
    val title: String = "",
    val detail: String = "",
    val model: String = "",
    val latencyMillis: Long? = null,
)

enum class DesktopTransferPhase {
    IDLE,
    PAIRING,
    CHECKING,
    SENDING,
    PAUSED,
    COMPLETED,
    FAILED,
}

/** Token-free transfer state rendered by the settings and delivery views. */
data class DesktopTransferUiState(
    val phase: DesktopTransferPhase = DesktopTransferPhase.IDLE,
    val taskId: String? = null,
    val fileName: String = "",
    val progress: Float = 0f,
    val sentBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val completedChunks: Int = 0,
    val totalChunks: Int = 0,
    val message: String = "",
    val receipt: ReceiverTransferReceipt? = null,
) {
    val isWorking: Boolean
        get() = phase == DesktopTransferPhase.PAIRING ||
            phase == DesktopTransferPhase.CHECKING ||
            phase == DesktopTransferPhase.SENDING
}

private const val ALARM_REVIEW_DEFAULT_NOTE = "现场确认后记录"

data class RackExcelUiState(
    val modelProfiles: List<ModelProfile> = emptyList(),
    val activeModelProfileId: String = "",
    val modelProfileEditorId: String = "",
    val modelProfileName: String = "",
    val url: String = "",
    val model: String = "",
    val apiKey: String = "",
    val roomName: String = "",
    val images: List<SelectedImage> = emptyList(),
    val imageOutcomes: List<ImageOutcome> = emptyList(),
    val activeTaskId: String? = null,
    val taskStartedAtMillis: Long? = null,
    val taskFinishedAtMillis: Long? = null,
    val recognitionFinishedAtMillis: Long? = null,
    val reviewStartedAtMillis: Long? = null,
    val concurrency: Int = TaskPolicy.DEFAULT_CONCURRENCY,
    val isRunning: Boolean = false,
    val isTestingConnection: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val error: String? = null,
    val engineBanner: EngineBanner = EngineBanner(),
    val racks: List<Rack> = emptyList(),
    val resultUri: Uri? = null,
    val resultName: String? = null,
    val efficiency: TaskEfficiencyMetrics = TaskEfficiencyMetrics.EMPTY,
    val confirmationMode: String = "未确认",
    val exportPhase: ExportPhase = ExportPhase.IDLE,
    val exportProgress: Float = 0f,
    val exportEllipsisCount: Int = 0,
    val exportTargetMillis: Long = 0L,
    val exportActualWriteMillis: Long? = null,
    val filePrefix: String = "云枢智维",
    val autoSave: Boolean = true,
    val retainOriginalImages: Boolean = false,
    val promptTemplates: List<PromptTemplate> = emptyList(),
    val activePromptId: String = "",
    val promptEditorId: String = "",
    val promptEditorName: String = "",
    val promptEditorVersion: String = "",
    val promptEditorContent: String = "",
    val taskHistory: List<TaskHistoryItem> = emptyList(),
    val connectedComputer: ConnectedComputer? = null,
    val desktopTransfer: DesktopTransferUiState = DesktopTransferUiState(),
    val desktopLastCheckedAtMillis: Long? = null,
    val desktopLastCheckSucceeded: Boolean? = null,
    val alarmSettings: AlarmSettings = AlarmSettingsDefaults.SETTINGS,
    val alarms: List<AlarmRecord> = emptyList(),
    val alarmMatches: List<AlarmMatchResult> = emptyList(),
    val alarmHistory: Map<String, List<AlarmRecord>> = emptyMap(),
    val alarmEffect: AlarmUiEffect? = null,
    val networkGateway: NetworkGatewayConfig = NetworkGatewayConfig(),
    val isTestingGateway: Boolean = false,
) {
    val activePrompt: PromptTemplate?
        get() = promptTemplates.firstOrNull { it.id == activePromptId }
}

class RackExcelViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = SecureConfigStore(application)
    private val settingsStore = AppSettingsStore(application)
    private val promptStore = PromptTemplateStore(application)
    private val historyStore = TaskHistoryStore(application)
    private val taskSessionStore = TaskSessionStore(application)
    private val taskArchiveStore = TaskArchiveStore(application)
    private val connectedComputerStore = ConnectedComputerStore(application)
    private val alarmSettingsStore = AlarmSettingsStore(application)
    private val networkGatewayConfigStore = NetworkGatewayConfigStore(application)
    private val alarmSourceFactory = AlarmSourceFactory(application)
    private var alarmSource: AlarmSource
    private val apiClient = VisionApiClient()
    private val desktopReceiverClient = DesktopReceiverClient()
    private val retryExecutor = RetryExecutor(maxRetries = TaskPolicy.MAX_RETRIES)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<RackExcelUiState> = _state.asStateFlow()
    private var lastBytes: ByteArray? = null
    private var isPreservingPreviousDelivery = false
    private var desktopUploadJob: Job? = null
    /** Invalidates late progress/completion callbacks from an older transfer. */
    private val desktopUploadGeneration = AtomicLong(0L)
    private val alarmEffectSequence = AtomicLong(0L)

    init {
        alarmSource = alarmSourceFactory.create(
            alarmSettingsStore.load().sourceMode,
            networkGatewayConfigStore.load(),
        )
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            state.collect { snapshot ->
                if (
                    snapshot.activeTaskId != null ||
                    snapshot.images.isNotEmpty() ||
                    snapshot.imageOutcomes.isNotEmpty() ||
                    snapshot.resultUri != null
                ) {
                    taskSessionStore.save(snapshot.toActiveTaskSession())
                } else {
                    taskSessionStore.clear()
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadAlarmRecords(showStatus = false)
        }
    }

    fun setModelProfileName(value: String) = _state.update { it.copy(modelProfileName = value, error = null) }
    fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }
    fun setModel(value: String) = _state.update { it.copy(model = value, error = null) }
    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun setRoomName(value: String) = _state.update { it.copy(roomName = value, error = null) }
    fun setConcurrency(value: Int) = _state.update {
        it.copy(concurrency = value.coerceIn(1, TaskPolicy.MAX_CONCURRENCY), error = null)
    }

    fun setAlarmSourceMode(mode: AlarmSourceMode) {
        val settings = alarmSettingsStore.setSourceMode(mode)
        alarmSource = alarmSourceFactory.create(settings.sourceMode, _state.value.networkGateway)
        _state.update {
            it.copy(
                alarmSettings = settings,
                alarms = emptyList(),
                alarmMatches = emptyList(),
                status = "已切换告警数据源：${settings.sourceMode.label}。",
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { loadAlarmRecords(showStatus = true) }
    }

    /** Updates the editable gateway form without making a network request. */
    fun updateNetworkGateway(transform: (NetworkGatewayConfig) -> NetworkGatewayConfig) {
        _state.update { current ->
            current.copy(
                networkGateway = transform(current.networkGateway).copy(
                    lastCheckAtMillis = null,
                    lastLatencyMillis = null,
                    lastCheckSucceeded = null,
                    lastCheckMessage = "尚未检测",
                ),
                error = null,
            )
        }
    }

    fun setNetworkGateway(config: NetworkGatewayConfig) = updateNetworkGateway { config }

    fun saveNetworkGatewayConfig() {
        val saved = networkGatewayConfigStore.save(_state.value.networkGateway)
        alarmSource = alarmSourceFactory.create(_state.value.alarmSettings.sourceMode, saved)
        _state.update { it.copy(networkGateway = saved, status = "网管接口配置已保存，可执行连通性测试。", error = null) }
    }

    fun testNetworkGateway() {
        val current = _state.value
        val normalized = networkGatewayConfigStore.save(current.networkGateway)
        val gateway = ConfiguredNetworkManagerGateway(normalized)
        _state.update { it.copy(networkGateway = normalized, isTestingGateway = true, status = "正在检测网管接口链路…", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = gateway.check()
            val checked = normalized.copy(
                lastCheckAtMillis = System.currentTimeMillis(),
                lastLatencyMillis = result.latencyMillis,
                lastCheckMessage = result.message,
                lastCheckSucceeded = result.succeeded,
            )
            networkGatewayConfigStore.save(checked)
            _state.update {
                it.copy(
                    networkGateway = checked,
                    isTestingGateway = false,
                    status = result.message,
                    error = if (result.succeeded) null else result.message,
                )
            }
        }
    }

    fun setAlarmDemoMode(enabled: Boolean) {
        val settings = alarmSettingsStore.setDemoMode(enabled)
        _state.update { it.copy(alarmSettings = settings, error = null) }
        if (enabled) {
            seedDemoAlarmsForCurrentTask()
        } else {
            _state.update { it.copy(status = "演示模式已关闭；当前告警记录仍可继续查看。", error = null) }
        }
    }

    fun setAlarmSoundEnabled(enabled: Boolean) {
        val settings = alarmSettingsStore.setSoundEnabled(enabled)
        _state.update { it.copy(alarmSettings = settings, status = "声音提醒已${if (enabled) "开启" else "关闭"}。", error = null) }
    }

    fun setAlarmVibrationEnabled(enabled: Boolean) {
        val settings = alarmSettingsStore.setVibrationEnabled(enabled)
        _state.update { it.copy(alarmSettings = settings, status = "震动提醒已${if (enabled) "开启" else "关闭"}。", error = null) }
    }

    fun setAlarmReviewer(value: String) {
        val settings = alarmSettingsStore.setReviewer(value)
        _state.update { it.copy(alarmSettings = settings, error = null) }
    }

    fun setAlarmMatchField(field: com.rackexcel.mobile.alarm.AlarmIdentityField, enabled: Boolean) {
        val current = _state.value.alarmSettings.matchConfig.fields
        val fields = if (enabled) current + field else current - field
        val settings = alarmSettingsStore.setMatchConfig(
            AlarmMatchConfig(fields = fields, mode = _state.value.alarmSettings.matchConfig.mode),
        )
        _state.update { it.copy(alarmSettings = settings, alarmMatches = alarmMatchesFor(it, it.alarms), error = null) }
    }

    fun setAlarmMatchMode(mode: AlarmMatchMode) {
        val settings = alarmSettingsStore.setMatchConfig(
            _state.value.alarmSettings.matchConfig.copy(mode = mode),
        )
        _state.update { it.copy(alarmSettings = settings, alarmMatches = alarmMatchesFor(it, it.alarms), error = null) }
    }

    fun refreshAlarms() {
        viewModelScope.launch(Dispatchers.IO) { loadAlarmRecords(showStatus = true) }
    }

    fun importMockAlarmJson(uri: Uri) {
        if (_state.value.alarmSettings.sourceMode != AlarmSourceMode.MOCK) {
            _state.update { it.copy(error = "导入模拟告警前，请先切换到模拟告警源。") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val payload = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取所选 JSON 文件")
                val mock = alarmSource as? MockAlarmSource ?: error("当前告警数据源不是模拟源")
                mock.importJson(payload)
            }.onSuccess { loaded ->
                _state.update { current ->
                    val scoped = loaded.filter { alarm -> current.activeTaskId != null && (alarm.taskId == current.activeTaskId || alarm.taskId == null) }
                    current.copy(
                        alarms = scoped,
                        alarmMatches = alarmMatchesFor(current.copy(alarms = scoped), scoped),
                        status = "已导入 ${loaded.size} 条本地模拟告警。",
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(error = "导入模拟告警失败：${readableError(error)}") }
            }
        }
    }

    /** Public demo action used by the alarm page; records are based on current racks. */
    fun seedDemoAlarms() {
        seedDemoAlarmsForCurrentTask()
    }

    /** Injects one additional prompt-level alarm against a real current-task device. */
    fun injectDemoAlarm() {
        val snapshot = _state.value
        if (snapshot.alarmSettings.sourceMode != AlarmSourceMode.MOCK) {
            _state.update { it.copy(error = "手动注入仅适用于模拟告警源，请先切换数据源。") }
            return
        }
        val target = snapshot.racks.asSequence().flatMap { rack ->
            rack.devices.asSequence().map { device -> rack to device }
        }.firstOrNull()
        if (target == null) {
            _state.update { it.copy(status = "当前任务尚无已识别设备，请先完成一次识别。", error = null) }
            return
        }
        val (rack, device) = target
        val now = System.currentTimeMillis()
        val alarm = AlarmRecord(
            alarmId = "MOCK-INJECT-$now",
            identity = AlarmDeviceIdentityResolver.forDevice(rack, device),
            severity = AlarmSeverity.INFO,
            occurredAtMillis = now,
            description = "手动注入演示告警：请现场确认该设备状态",
            taskId = snapshot.activeTaskId,
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                alarmSource.injectAlarm(alarm)
            }.onSuccess {
                _state.update { current ->
                    val alarms = current.alarms + alarm
                    current.copy(
                        alarms = alarms,
                        alarmMatches = alarmMatchesFor(current.copy(alarms = alarms), alarms),
                        alarmEffect = current.newAlarmEffect(alarm),
                        status = "已注入 1 条模拟告警，可点击告警设备进入现场复核。",
                        error = null,
                    )
                }
                persistAlarmHistory()
            }.onFailure { error ->
                _state.update { it.copy(error = "注入模拟告警失败：${readableError(error)}") }
            }
        }
    }

    /** Applies exactly one human decision and keeps the current alarm question open. */
    fun reviewAlarm(
        alarmId: String,
        decision: AlarmReviewDecision,
        note: String,
        evidencePhotoPaths: List<String> = emptyList(),
    ) {
        val snapshot = _state.value
        val alarm = snapshot.alarms.firstOrNull { it.alarmId == alarmId } ?: return
        val match = snapshot.alarmMatches.firstOrNull { it.alarm.alarmId == alarmId }
        val coordinate = (match as? AlarmMatchResult.Matched)?.coordinate
        val inheritedEvidence = coordinate?.let { target ->
            snapshot.imageOutcomes.firstOrNull { outcome ->
                val rack = outcome.rack ?: return@firstOrNull false
                rack.cabinetId == target.cabinetId && rack.devices.getOrNull(target.deviceIndex)?.let { device ->
                    device.bottomU == target.bottomU && device.heightU == target.heightU && device.type == target.type
                } == true
            }?.reviewImagePath
        }?.let(::listOf).orEmpty()
        val transition = runCatching {
            AlarmReviewStateMachine.apply(
                alarm = alarm,
                coordinate = coordinate,
                input = AlarmReviewInput(
                    decision = decision,
                    reviewer = snapshot.alarmSettings.reviewer,
                    note = note.ifBlank { ALARM_REVIEW_DEFAULT_NOTE },
                    evidencePhotoPaths = evidencePhotoPaths.ifEmpty { inheritedEvidence },
                ),
            )
        }.getOrElse { error ->
            _state.update { it.copy(error = "告警复核未提交：${readableError(error)}") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { alarmSource.updateAlarm(transition.updatedAlarm) }
                .onSuccess { updatedAlarm ->
                    val targetOutcomeIndex = snapshot.imageOutcomes.indexOfFirst { outcome ->
                        val rack = outcome.rack ?: return@indexOfFirst false
                        coordinate?.let { c ->
                            rack.cabinetId == c.cabinetId && rack.devices.getOrNull(c.deviceIndex)?.let { d ->
                                d.bottomU == c.bottomU && d.heightU == c.heightU && d.type == c.type
                            } == true
                        } ?: false
                    }
                    val writeback = if (targetOutcomeIndex >= 0 && coordinate != null) {
                        AlarmAssetWritebackPolicy.apply(
                            listOfNotNull(snapshot.imageOutcomes[targetOutcomeIndex].rack),
                            transition,
                        )
                    } else {
                        AlarmAssetWritebackPolicy.apply(snapshot.racks, transition)
                    }
                    val updatedOutcomes = if (targetOutcomeIndex >= 0 && writeback.applied) {
                        val updatedRack = writeback.racks.firstOrNull()
                        if (updatedRack != null) {
                            snapshot.imageOutcomes.mapIndexed { index, outcome ->
                                if (index == targetOutcomeIndex) outcome.copy(
                                    rack = updatedRack,
                                    state = ImageProcessingState.QUALITY_REVIEW,
                                    reviewConfirmed = false,
                                    message = when (decision) {
                                        AlarmReviewDecision.DEVICE_NOT_PRESENT -> "告警复核已确认设备不存在，上架图待重新交付。"
                                        AlarmReviewDecision.DEVICE_FAULT -> "告警复核已确认设备故障，已生成处置记录。"
                                        AlarmReviewDecision.NEEDS_FURTHER_PROCESSING -> "已创建进一步处理待办，告警仍待闭环。"
                                    },
                                ) else outcome
                            }
                        } else snapshot.imageOutcomes
                    } else snapshot.imageOutcomes
                    invalidateCurrentDelivery(snapshot.activeTaskId)
                    val alarms = snapshot.alarms.map { current ->
                        if (current.alarmId == updatedAlarm.alarmId) updatedAlarm else current
                    }
                    val racks = RackOrdering.sort(updatedOutcomes.mapNotNull(ImageOutcome::rack)).racks
                    _state.update { current ->
                        if (current.activeTaskId != snapshot.activeTaskId) return@update current
                        val next = current.copy(
                            alarms = alarms,
                            alarmMatches = alarmMatchesFor(current.copy(alarms = alarms, racks = racks), alarms),
                            alarmHistory = current.alarmHistory + ((current.activeTaskId ?: "") to alarms.filter { it.taskId == current.activeTaskId }),
                            imageOutcomes = updatedOutcomes,
                            racks = racks,
                            resultUri = null,
                            resultName = null,
                            exportPhase = ExportPhase.WAITING_CONFIRMATION,
                            status = when (decision) {
                                AlarmReviewDecision.DEVICE_NOT_PRESENT -> "已记录设备不存在：上架图与台账待重新交付，告警已处理。"
                                AlarmReviewDecision.DEVICE_FAULT -> "已记录设备故障：处置记录与证据已留存，告警已处理。"
                                AlarmReviewDecision.NEEDS_FURTHER_PROCESSING -> "已创建进一步处理待办；告警保持未处理，等待后续闭环。"
                            },
                            error = null,
                        )
                        next
                    }
                    persistAlarmHistory()
                }
                .onFailure { error ->
                    _state.update { it.copy(error = "告警状态回写失败：${readableError(error)}") }
                }
        }
    }

    fun alarmsForTask(taskId: String): List<AlarmRecord> = _state.value.alarmHistory[taskId].orEmpty()

    fun activateModelProfile(id: String) {
        val snapshot = _state.value
        val selected = snapshot.modelProfiles.firstOrNull { it.id == id } ?: return
        val collection = ModelProfilePolicy.normalize(snapshot.modelProfiles, id, selected)
        applyModelProfileCollection(collection, "已切换模型配置：" + collection.active.name)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { configStore.saveProfiles(collection) } }
                .onFailure { error -> _state.update { it.copy(error = "模型配置切换出现问题：" + readableError(error)) } }
        }
    }

    fun createModelProfile() {
        val snapshot = _state.value
        val source = snapshot.currentModelProfile()
        val draft = source.copy(
            id = "profile-" + UUID.randomUUID(),
            name = source.name + " 副本",
            isBuiltIn = false,
        )
        val collection = ModelProfilePolicy.normalize(snapshot.modelProfiles + draft, draft.id, source)
        applyModelProfileCollection(collection, "已新建模型配置副本，请填写名称和参数后保存。")
    }

    fun deleteActiveModelProfile() {
        val snapshot = _state.value
        val active = snapshot.currentModelProfile()
        if (active.isBuiltIn) {
            _state.update { it.copy(status = "平台默认配置作为识别基线保留。", error = null) }
            return
        }
        val profiles = ModelProfilePolicy.remove(snapshot.modelProfiles, active.id, snapshot.modelProfiles.first())
        val collection = ModelProfilePolicy.normalize(profiles, "", snapshot.modelProfiles.first())
        applyModelProfileCollection(collection, "已删除模型配置，当前使用：" + collection.active.name)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { configStore.saveProfiles(collection) } }
                .onFailure { error -> _state.update { it.copy(error = "模型配置删除出现问题：" + readableError(error)) } }
        }
    }

    fun addImages(items: List<SelectedImage>) {
        val snapshot = _state.value
        if (snapshot.isRunning) {
            _state.update { it.copy(status = "当前识别任务正在运行，完成后即可开始下一轮现场采集。", error = null) }
            return
        }
        if (snapshot.desktopTransfer.isWorking) {
            _state.update {
                it.copy(
                    status = "当前 Excel 正在发送到电脑，请等待发送完成或暂停后再开始新的现场采集任务。",
                    error = null,
                )
            }
            return
        }
        val startsFreshTask = !snapshot.isRunning && snapshot.resultName != null
        if (startsFreshTask && snapshot.resultUri == null && lastBytes != null) {
            if (isPreservingPreviousDelivery) {
                _state.update { it.copy(status = "正在保留上一轮 Excel，完成后将自动开始新的现场采集任务。", error = null) }
                return
            }
            preservePreviousDeliveryThenAddImages(snapshot, items)
            return
        }
        if (startsFreshTask) {
            // A completed delivery owns the current image queue. The first
            // image added after delivery starts a new task while the completed
            // task remains available through history.
            desktopUploadJob?.cancel()
            desktopUploadGeneration.incrementAndGet()
        }
        val currentImages = if (startsFreshTask) emptyList() else snapshot.images
        val existing = currentImages.map { it.uri }.toSet()
        val added = items.filterNot { it.uri in existing }
        if (added.isEmpty()) {
            _state.update { it.copy(status = "图片已在当前队列中。", error = null) }
            return
        }
        val now = System.currentTimeMillis()
        val taskId = if (startsFreshTask) "TASK-$now" else snapshot.activeTaskId ?: "TASK-$now"
        val startedAt = if (startsFreshTask) now else snapshot.taskStartedAtMillis ?: now
        if (startsFreshTask) lastBytes = null
        _state.update { current ->
            current.copy(
                images = currentImages + added,
                imageOutcomes = if (startsFreshTask) emptyList() else current.imageOutcomes,
                activeTaskId = taskId,
                taskStartedAtMillis = startedAt,
                taskFinishedAtMillis = if (startsFreshTask) null else current.taskFinishedAtMillis,
                recognitionFinishedAtMillis = if (startsFreshTask) null else current.recognitionFinishedAtMillis,
                reviewStartedAtMillis = if (startsFreshTask) null else current.reviewStartedAtMillis,
                roomName = if (startsFreshTask) "" else current.roomName,
                racks = if (startsFreshTask) emptyList() else current.racks,
                resultUri = if (startsFreshTask) null else current.resultUri,
                resultName = if (startsFreshTask) null else current.resultName,
                efficiency = if (startsFreshTask) TaskEfficiencyMetrics.EMPTY else current.efficiency,
                confirmationMode = if (startsFreshTask) "未确认" else current.confirmationMode,
                exportPhase = if (startsFreshTask) ExportPhase.IDLE else current.exportPhase,
                exportProgress = if (startsFreshTask) 0f else current.exportProgress,
                exportEllipsisCount = if (startsFreshTask) 0 else current.exportEllipsisCount,
                exportTargetMillis = if (startsFreshTask) 0L else current.exportTargetMillis,
                exportActualWriteMillis = if (startsFreshTask) null else current.exportActualWriteMillis,
                progress = if (startsFreshTask) 0f else current.progress,
                desktopTransfer = if (startsFreshTask) DesktopTransferUiState() else current.desktopTransfer,
                alarms = if (startsFreshTask) emptyList() else current.alarms,
                alarmMatches = if (startsFreshTask) emptyList() else current.alarmMatches,
                alarmEffect = if (startsFreshTask) null else current.alarmEffect,
                error = null,
                status = if (startsFreshTask) {
                    "已开启新的现场采集任务，并加入 ${added.size} 张机柜图片。"
                } else {
                    "已加入 ${added.size} 张机柜图片，等待智能识别。"
                },
            )
        }
        added.forEach { image ->
            viewModelScope.launch {
                val archive = runCatching {
                    withContext(Dispatchers.IO) {
                        taskArchiveStore.archive(taskId, image.id, image.uri, snapshot.retainOriginalImages)
                    }
                }.getOrNull()
                val result = runCatching {
                    withContext(Dispatchers.IO) { ImagePreprocessor.inspectQuality(getApplication(), image.uri) }
                }.getOrNull()
                if (result != null || archive != null) {
                    _state.update { current ->
                        if (current.activeTaskId != taskId || current.images.none { it.id == image.id }) {
                            return@update current
                        }
                        current.copy(images = current.images.map { item ->
                            if (item.id == image.id) {
                                item.copy(
                                    quality = result ?: item.quality,
                                    archivedPath = archive?.reviewImagePath ?: item.archivedPath,
                                    originalPath = archive?.originalImagePath ?: item.originalPath,
                                )
                            } else item
                        })
                    }
                }
            }
        }
        refreshAlarmMatches()
    }

    fun removeImage(uri: Uri) {
        val snapshot = _state.value
        val removed = snapshot.images.firstOrNull { it.uri == uri } ?: return
        if (snapshot.resultName != null) {
            invalidateCurrentDelivery(snapshot.activeTaskId)
        }
        _state.update { current ->
            val remaining = current.images.filterNot { it.id == removed.id }
            val outcomes = current.imageOutcomes.filterNot { it.imageId == removed.id }
            val racks = RackOrdering.sort(outcomes.mapNotNull(ImageOutcome::rack)).racks
            if (remaining.isEmpty() && outcomes.isEmpty() && current.resultUri == null) {
                current.copy(
                    images = emptyList(),
                    imageOutcomes = emptyList(),
                    racks = emptyList(),
                    activeTaskId = null,
                    taskStartedAtMillis = null,
                    taskFinishedAtMillis = null,
                    progress = 0f,
                    error = null,
                    status = "图片队列已清空，可开始新的现场采集。",
                )
            } else {
                current.copy(
                    images = remaining,
                    imageOutcomes = outcomes,
                    racks = racks,
                    resultUri = if (snapshot.resultName != null) null else current.resultUri,
                    resultName = if (snapshot.resultName != null) null else current.resultName,
                    taskFinishedAtMillis = if (snapshot.resultName != null) null else current.taskFinishedAtMillis,
                    error = null,
                    status = if (snapshot.resultName != null) {
                        "图片已移除，原交付已过期，请重新识别后生成新版 Excel。"
                    } else {
                        "图片已从当前任务移除。"
                    },
                )
            }
        }
        refreshAlarmMatches()
    }

    fun beginNewTask() {
        val snapshot = _state.value
        if (snapshot.isRunning) return
        desktopUploadJob?.cancel()
        desktopUploadGeneration.incrementAndGet()
        lastBytes = null
        _state.update {
            it.copy(
                roomName = "",
                images = emptyList(),
                imageOutcomes = emptyList(),
                activeTaskId = null,
                taskStartedAtMillis = null,
                taskFinishedAtMillis = null,
                recognitionFinishedAtMillis = null,
                reviewStartedAtMillis = null,
                racks = emptyList(),
                resultUri = null,
                resultName = null,
                efficiency = TaskEfficiencyMetrics.EMPTY,
                confirmationMode = "未确认",
                exportPhase = ExportPhase.IDLE,
                exportProgress = 0f,
                exportEllipsisCount = 0,
                exportTargetMillis = 0L,
                exportActualWriteMillis = null,
                progress = 0f,
                desktopTransfer = DesktopTransferUiState(),
                alarms = emptyList(),
                alarmMatches = emptyList(),
                alarmEffect = null,
                status = "已建立新的现场采集任务。",
                error = null,
            )
        }
        refreshAlarmMatches()
    }

    fun saveConfig() {
        val snapshot = _state.value
        if (snapshot.modelProfileName.trim().isBlank()) {
            _state.update { it.copy(error = "请填写模型配置名称。") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val collection = snapshot.toProfileCollection()
                withContext(Dispatchers.IO) { configStore.saveProfiles(collection) }
                collection
            }.onSuccess { collection ->
                applyModelProfileCollection(collection, "模型配置已安全保存：" + collection.active.name)
            }.onFailure { error ->
                _state.update { it.copy(error = "配置保存出现问题：" + readableError(error)) }
            }
        }
    }

    fun testConnection() {
        val snapshot = _state.value
        if (snapshot.isRunning || snapshot.isTestingConnection) return
        val config = snapshot.toConfig()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isTestingConnection = true,
                    error = null,
                    engineBanner = EngineBanner(
                        phase = EngineBannerPhase.CHECKING,
                        title = "正在进行智能识别链路自检…",
                        detail = "正在校验模型、视觉识别通道和 JSON 输出能力。",
                        model = config.model,
                    ),
                )
            }
            try {
                withContext(Dispatchers.IO) { configStore.saveProfiles(snapshot.toProfileCollection()) }
                val retryResult = retryExecutor.attempt(
                    onRetry = { retry, _ ->
                        _state.update {
                            it.copy(
                                status = TaskPolicy.repairStatus(retry),
                                error = null,
                                engineBanner = EngineBanner(
                                    phase = EngineBannerPhase.CHECKING,
                                    title = TaskPolicy.repairStatus(retry),
                                    detail = "模型服务出现短时波动，正在再次校验视觉识别通道。",
                                    model = config.model,
                                ),
                            )
                        }
                    },
                    shouldRetry = ConnectionRetryPolicy::shouldRetry,
                ) { apiClient.checkConnection(config) }
                val result = retryResult.value
                    ?: throw (retryResult.error ?: VisionApiException("模型链路自检未返回结果"))
                val healthCollection = snapshot.toProfileCollection().withActiveHealth(
                    checkedAtMillis = System.currentTimeMillis(),
                    latencyMillis = result.latencyMillis,
                    succeeded = true,
                )
                withContext(Dispatchers.IO) { configStore.saveProfiles(healthCollection) }
                _state.update {
                    it.copy(
                        modelProfiles = healthCollection.profiles,
                        isTestingConnection = false,
                        status = "智能识别引擎已就绪",
                        error = null,
                        engineBanner = EngineBanner(
                            phase = EngineBannerPhase.READY,
                            title = "智能识别引擎已就绪",
                            detail = "模型连接正常，视觉识别通道已完成校验。当前状态：待命",
                            model = result.model,
                            latencyMillis = result.latencyMillis,
                        ),
                    )
                }
            } catch (error: Throwable) {
                val healthCollection = snapshot.toProfileCollection().withActiveHealth(
                    checkedAtMillis = System.currentTimeMillis(),
                    latencyMillis = null,
                    succeeded = false,
                )
                runCatching {
                    withContext(Dispatchers.IO) { configStore.saveProfiles(healthCollection) }
                }
                _state.update {
                    it.copy(
                        modelProfiles = healthCollection.profiles,
                        isTestingConnection = false,
                        status = "智能识别引擎暂未就绪",
                        error = readableError(error),
                        engineBanner = EngineBanner(
                            phase = EngineBannerPhase.FAILED,
                            title = "智能识别引擎暂未就绪",
                            detail = "请检查模型配置后再次执行链路自检。",
                            model = config.model,
                        ),
                    )
                }
            }
        }
    }

    fun activatePrompt(id: String) {
        val template = _state.value.promptTemplates.firstOrNull { it.id == id } ?: return
        promptStore.setActive(template.id)
        setPromptState(template, "已启用提示词模板：" + template.name)
    }

    fun setPromptEditorName(value: String) = _state.update { it.copy(promptEditorName = value, error = null) }
    fun setPromptEditorVersion(value: String) = _state.update { it.copy(promptEditorVersion = value, error = null) }
    fun setPromptEditorContent(value: String) = _state.update { it.copy(promptEditorContent = value, error = null) }

    fun savePromptTemplate() {
        val snapshot = _state.value
        val name = snapshot.promptEditorName.trim()
        val content = snapshot.promptEditorContent.trim()
        if (name.isBlank() || content.isBlank()) {
            _state.update { it.copy(error = "请填写提示词模板名称和内容。") }
            return
        }
        val current = snapshot.promptTemplates.firstOrNull { it.id == snapshot.promptEditorId }
        val id = if (current?.isBuiltIn == false) current.id else "custom-" + UUID.randomUUID()
        val template = PromptTemplate(
            id = id,
            name = name,
            version = snapshot.promptEditorVersion.trim().ifBlank { "自定义" },
            content = content,
            updatedAtMillis = System.currentTimeMillis(),
            isBuiltIn = false,
        )
        runCatching {
            promptStore.save(template)
            promptStore.setActive(template.id)
        }.onSuccess {
            setPromptState(template, "提示词模板已保存并启用。")
        }.onFailure { error ->
            _state.update { it.copy(error = "提示词保存出现问题：" + readableError(error)) }
        }
    }

    fun duplicateActivePrompt() {
        val source = _state.value.activePrompt ?: return
        val duplicate = PromptTemplate(
            id = "custom-" + UUID.randomUUID(),
            name = source.name + " 副本",
            version = source.version,
            content = source.content,
            updatedAtMillis = System.currentTimeMillis(),
            isBuiltIn = false,
        )
        promptStore.save(duplicate)
        promptStore.setActive(duplicate.id)
        setPromptState(duplicate, "已创建提示词副本，可继续编辑后保存。")
    }

    fun deleteActivePrompt() {
        val active = _state.value.activePrompt ?: return
        if (active.isBuiltIn) {
            _state.update { it.copy(status = "标准模板作为识别基线保留。", error = null) }
            return
        }
        promptStore.delete(active.id)
        setPromptState(promptStore.active(), "自定义提示词模板已删除。")
    }

    fun resetPromptEditor() {
        val active = _state.value.activePrompt ?: return
        _state.update {
            it.copy(
                promptEditorId = active.id,
                promptEditorName = active.name,
                promptEditorVersion = active.version,
                promptEditorContent = active.content,
                status = "已恢复当前启用模板的已保存内容。",
                error = null,
            )
        }
    }

    fun setFilePrefix(value: String) = _state.update { it.copy(filePrefix = value, error = null) }
    fun setAutoSave(value: Boolean) = _state.update { it.copy(autoSave = value, error = null) }
    fun setRetainOriginalImages(value: Boolean) = _state.update { it.copy(retainOriginalImages = value, error = null) }

    fun saveAppSettings() {
        val snapshot = _state.value
        settingsStore.save(
            AppSettings(
                filePrefix = snapshot.filePrefix,
                autoSave = snapshot.autoSave,
                retainOriginalImages = snapshot.retainOriginalImages,
            ),
        )
        _state.update { it.copy(status = "文件与数据设置已保存。", error = null) }
    }

    fun clearTaskHistory() {
        val snapshot = _state.value
        if (snapshot.isRunning) {
            _state.update { it.copy(status = "识别进行中，任务结束后可清理历史记录。", error = null) }
            return
        }
        val history = snapshot.taskHistory
        // Clearing all records also clears an unfinished active session. It is
        // not present in taskHistory yet, but TaskSessionStore would otherwise
        // restore that queue after the next process restart.
        val clearCurrentWorkspace = !snapshot.isRunning && snapshot.activeTaskId != null
        val archiveTaskIds = buildList {
            addAll(history.map(TaskHistoryItem::taskId))
            snapshot.activeTaskId?.let(::add)
        }.distinct()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                archiveTaskIds.forEach { taskArchiveStore.deleteTaskArchive(it) }
                historyStore.clear()
                if (clearCurrentWorkspace) taskSessionStore.clear()
            }
            _state.update { current ->
                if (clearCurrentWorkspace && current.activeTaskId == snapshot.activeTaskId) {
                    current.clearCompletedWorkspace(
                        status = "历史任务与本地复核图片已清理，可开始新的现场采集。",
                    ).copy(alarmHistory = emptyMap())
                } else {
                    current.copy(
                        taskHistory = emptyList(),
                        alarmHistory = emptyMap(),
                        status = "历史任务与本地复核图片已清理。",
                        error = null,
                    )
                }
            }
        }
    }

    fun deleteTaskHistory(taskId: String) {
        val snapshot = _state.value
        val historyItem = snapshot.taskHistory.firstOrNull { it.taskId == taskId } ?: return
        if (snapshot.isRunning) {
            _state.update { it.copy(status = "识别进行中，任务结束后可清理历史记录。", error = null) }
            return
        }
        val clearCurrentWorkspace = !snapshot.isRunning && snapshot.activeTaskId == taskId
        viewModelScope.launch {
            val remainingHistory = withContext(Dispatchers.IO) {
                taskArchiveStore.deleteTaskArchive(historyItem.taskId)
                historyStore.remove(historyItem.taskId)
                if (clearCurrentWorkspace) taskSessionStore.clear()
                historyStore.load()
            }
            _state.update { current ->
                if (clearCurrentWorkspace && current.activeTaskId == taskId) {
                    current.clearCompletedWorkspace(
                        status = "历史任务已删除，可开始新的现场采集。",
                        taskHistory = remainingHistory,
                    ).copy(alarmHistory = current.alarmHistory - taskId)
                } else {
                    current.copy(
                        taskHistory = remainingHistory,
                        alarmHistory = current.alarmHistory - taskId,
                        status = "历史任务已删除。",
                        error = null,
                    )
                }
            }
        }
    }

    fun generate() {
        val snapshot = _state.value
        if (snapshot.exportPhase == ExportPhase.WAITING_CONFIRMATION) {
            _state.update {
                it.copy(status = "识别结果已准备好，请确认无疑义或进入现场复核后生成 Excel。", error = null)
            }
            return
        }
        if (TaskPolicy.shouldBlockDuplicateGenerate(
                isRunning = snapshot.isRunning,
                hasResult = snapshot.resultName != null,
                imageCount = snapshot.images.size,
            )
        ) {
            _state.update {
                it.copy(
                    status = "本次现场识别已完成；请点击开始采集建立新任务，或先处理待复核项。",
                    error = null,
                )
            }
            return
        }
        startTask(snapshot.images)
    }

    fun retryFailedImages() {
        val snapshot = _state.value
        val failedIds = snapshot.imageOutcomes
            .filter { it.state == ImageProcessingState.FAILED }
            .map { it.imageId }
            .toSet()
        if (failedIds.isEmpty()) {
            _state.update { it.copy(status = "当前没有修复失败的图片；待复核项可在任务中心逐项核对。", error = null) }
            return
        }
        retryImages(failedIds)
    }

    fun retryImage(imageId: String) = retryImages(setOf(imageId))

    fun updateReviewedDevice(
        imageId: String,
        deviceIndex: Int,
        type: String,
        bottomU: Int,
        heightU: Int,
    ) {
        val snapshot = _state.value
        val outcome = snapshot.imageOutcomes.firstOrNull { it.imageId == imageId } ?: return
        val rack = outcome.rack ?: return
        runCatching {
            RackEditPolicy.updateDevice(rack, deviceIndex, type, bottomU, heightU)
        }.onSuccess { edited ->
            val updated = outcome.copy(
                rack = edited,
                state = ImageProcessingState.QUALITY_REVIEW,
                reviewConfirmed = false,
                message = "已调整设备 U 位，等待现场确认后重新生成三表 Excel。",
            )
            applyLocalReviewUpdate(snapshot, updated, "已保存机柜布局调整，请完成现场确认。")
        }.onFailure { error ->
            _state.update { it.copy(error = readableError(error)) }
        }
    }

    /** Starts the manual-review stopwatch when the operator opens review. */
    fun beginReview() {
        if (_state.value.reviewStartedAtMillis != null) return
        _state.update { it.copy(reviewStartedAtMillis = System.currentTimeMillis()) }
    }

    fun confirmReview(imageId: String) {
        val snapshot = _state.value
        if (snapshot.exportPhase == ExportPhase.WRITING || snapshot.isRunning) return
        val outcome = snapshot.imageOutcomes.firstOrNull { it.imageId == imageId } ?: return
        if (outcome.rack == null) {
            _state.update { it.copy(status = "该图片尚未形成可确认的机柜结果，请重新识别或补拍。", error = null) }
            return
        }
        val updated = outcome.copy(
            state = ImageProcessingState.COMPLETED,
            reviewConfirmed = true,
            message = "现场复核已确认，等待确认生成三表 Excel。",
        )
        val outcomes = normalizeTaskOutcomes(
            TaskMergePolicy.replaceByKey(snapshot.imageOutcomes, listOf(updated), ImageOutcome::imageId),
        )
        val racks = RackOrdering.sort(outcomes.mapNotNull(ImageOutcome::rack)).racks
        val remaining = outcomes.any(ReviewPolicy::requiresManualReview)
        invalidateCurrentDelivery(snapshot.activeTaskId)
        _state.update {
            it.copy(
                imageOutcomes = outcomes,
                racks = racks,
                resultUri = null,
                resultName = null,
                taskFinishedAtMillis = null,
                exportPhase = ExportPhase.WAITING_CONFIRMATION,
                exportProgress = 0f,
                exportEllipsisCount = 0,
                exportTargetMillis = 0L,
                exportActualWriteMillis = null,
                efficiency = TaskEfficiencyMetrics.EMPTY,
                confirmationMode = "未确认",
                status = if (remaining) {
                    "本柜已确认，仍有 ${outcomes.count(ReviewPolicy::requiresManualReview)} 项待复核。"
                } else {
                    "全部机柜已复核，请确认生成三表 Excel。"
                },
                error = null,
            )
        }
        refreshAlarmMatches()
    }

    /** Allows a field operator to accept the current AI result without opening every review card. */
    fun confirmNoIssuesAndGenerate() {
        val snapshot = _state.value
        if (snapshot.resultName != null || snapshot.exportPhase == ExportPhase.COMPLETED) {
            _state.update {
                it.copy(
                    status = "本次三表 Excel 已交付。请点击开始新一轮采集，不会重复生成同一任务。",
                    error = null,
                )
            }
            return
        }
        if (snapshot.isRunning || snapshot.exportPhase == ExportPhase.WRITING) return
        if (snapshot.imageOutcomes.isEmpty() || snapshot.racks.isEmpty()) {
            _state.update { it.copy(status = "当前没有可确认的识别结果，请先完成图片识别。", error = null) }
            return
        }
        val mode = if (snapshot.imageOutcomes.any(ReviewPolicy::requiresManualReview)) {
            "存在待复核项，按当前结果确认"
        } else {
            "识别后直接确认"
        }
        startExportFromCurrentState(mode)
    }

    /** Compatibility alias for older UI builds. */
    fun confirmNoObjectionAndGenerate() = confirmNoIssuesAndGenerate()

    fun saveResult() {
        viewModelScope.launch {
            try {
                val snapshot = _state.value
                val bytes = readCurrentResultBytes(snapshot)
                val name = snapshot.resultName ?: "云枢智维_识别结果.xlsx"
                val downloadUri = withContext(Dispatchers.IO) {
                    FileResultStore.save(getApplication(), bytes, name)
                }
                val canonicalUri = snapshot.resultUri ?: downloadUri
                if (snapshot.resultUri == null) persistHistoryResult(canonicalUri)
                _state.update {
                    it.copy(resultUri = canonicalUri, status = "Excel 文件已保存到下载/云枢智维。", error = null)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { it.copy(error = "文件保存出现问题：" + readableError(error)) }
            }
        }
    }

    fun shareResult() {
        viewModelScope.launch {
            try {
                val uri = _state.value.resultUri ?: persistCurrentResult()
                FileResultStore.share(getApplication(), uri)
                persistHistoryResult(uri)
                _state.update {
                    it.copy(resultUri = uri, status = "已打开系统分享面板，可选择微信或其他应用。", error = null)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { it.copy(error = "打开分享面板出现问题：" + readableError(error)) }
            }
        }
    }

    fun openResult() {
        viewModelScope.launch {
            try {
                val uri = _state.value.resultUri ?: persistCurrentResult()
                FileResultStore.open(getApplication(), uri)
                persistHistoryResult(uri)
                _state.update {
                    it.copy(resultUri = uri, status = "已交由系统打开 Excel 文件。", error = null)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { it.copy(error = "打开文件出现问题：" + readableError(error)) }
            }
        }
    }

    fun openHistory(item: TaskHistoryItem) {
        val uri = resolveHistoryResultUri(item)
        if (uri == null) {
            _state.update {
                it.copy(status = "该任务未保存本地文件，可在新的识别任务中重新生成。", error = null)
            }
            return
        }
        persistRecoveredHistoryUri(item, uri)
        runCatching { FileResultStore.open(getApplication(), uri) }
            .onSuccess {
                _state.update {
                    it.copy(status = "已交由系统打开历史 Excel 文件。", error = null)
                }
            }
            .onFailure { error ->
                _state.update { it.copy(error = "打开历史文件出现问题：" + readableError(error)) }
        }
    }

    /**
     * Reopens an archived task as a new review session.  The original history
     * entry stays intact; once the operator confirms the edits, a revision is
     * exported as a separate history item with its own timestamp and workbook.
     */
    fun resumeHistoryForReview(item: TaskHistoryItem) {
        val current = _state.value
        if (current.isRunning) {
            _state.update { it.copy(status = "当前任务仍在处理，请稍候再打开历史复核。", error = null) }
            return
        }
        val now = System.currentTimeMillis()
        val revisionTaskId = "${item.taskId}-REV-$now"
        val outcomes = item.reviewItems.map { review ->
            val restoredState = ImageProcessingState.entries.firstOrNull { it.label == review.state }
                ?.takeUnless { it == ImageProcessingState.ANALYZING || it == ImageProcessingState.REPAIRING }
                ?: if (review.rack != null) ImageProcessingState.COMPLETED else ImageProcessingState.FAILED
            ImageOutcome(
                imageName = review.imageName,
                rack = review.rack,
                state = restoredState,
                retryCount = review.retryCount,
                message = review.message,
                repairEvents = review.repairEvents.mapIndexed { index, value ->
                    RepairEvent(index + 1, TaskHistoryText.normalizeRepairMessage(value))
                },
                imageId = review.imageId,
                // A revision starts a fresh confirmation pass, even when the
                // archived task had already been confirmed previously.
                reviewConfirmed = false,
                reviewImagePath = review.reviewImagePath,
            )
        }
        val images = item.reviewItems.map { review ->
            val archive = review.reviewImagePath?.let(::File)?.takeIf { it.isFile }
            SelectedImage(
                uri = archive?.let(Uri::fromFile) ?: Uri.EMPTY,
                name = review.imageName,
                id = review.imageId,
                archivedPath = archive?.absolutePath ?: review.reviewImagePath,
            )
        }
        val racks = RackOrdering.sort(outcomes.mapNotNull(ImageOutcome::rack)).racks
        _state.update {
            it.copy(
                roomName = item.roomName,
                images = images,
                imageOutcomes = outcomes,
                racks = racks,
                activeTaskId = revisionTaskId,
                taskStartedAtMillis = now,
                taskFinishedAtMillis = null,
                recognitionFinishedAtMillis = now,
                reviewStartedAtMillis = now,
                isRunning = false,
                progress = if (outcomes.isEmpty()) 0f else 1f,
                resultUri = null,
                resultName = null,
                efficiency = TaskEfficiencyMetrics.EMPTY,
                confirmationMode = "历史复核待确认",
                exportPhase = if (outcomes.isEmpty()) ExportPhase.IDLE else ExportPhase.WAITING_CONFIRMATION,
                exportProgress = 0f,
                exportEllipsisCount = 0,
                exportTargetMillis = 0L,
                exportActualWriteMillis = null,
                alarms = emptyList(),
                alarmMatches = emptyList(),
                status = "已载入历史任务，可复核修改后重新生成 Excel。",
                error = null,
            )
        }
    }

    fun shareHistory(item: TaskHistoryItem) {
        val uri = resolveHistoryResultUri(item)
        if (uri == null) {
            _state.update { it.copy(status = "该历史任务没有可分享的本地 Excel 文件。", error = null) }
            return
        }
        persistRecoveredHistoryUri(item, uri)
        runCatching { FileResultStore.share(getApplication(), uri) }
            .onSuccess {
                _state.update { it.copy(status = "已打开历史文件的系统分享面板，可选择微信或其他应用。", error = null) }
            }
            .onFailure { error ->
                _state.update { it.copy(error = "分享历史文件出现问题：" + readableError(error)) }
            }
    }

    fun pairDesktopReceiver(pairingUri: String) {
        if (_state.value.desktopTransfer.isWorking) return
        val rawUri = pairingUri.trim()
        if (rawUri.isBlank()) {
            _state.update { it.copy(error = "请扫描电脑端二维码，或粘贴完整的连接信息。") }
            return
        }
        viewModelScope.launch {
            var endpoint: String? = null
            _state.update {
                it.copy(
                    desktopTransfer = DesktopTransferUiState(
                        phase = DesktopTransferPhase.PAIRING,
                        message = "正在校验桌面接收器并建立加密配对。",
                    ),
                    error = null,
                )
            }
            try {
                val invite = ReceiverPairingUriParser.parse(rawUri)
                endpoint = "${invite.host}:${invite.port}"
                val connection = withContext(Dispatchers.IO) {
                    desktopReceiverClient.pair(
                        invite = invite,
                        deviceId = connectedComputerStore.deviceId(),
                        deviceName = "云枢智维现场手机",
                        onRetry = { retry, _ ->
                            _state.update {
                                it.copy(
                                    desktopTransfer = it.desktopTransfer.copy(
                                        message = "连接出现短时波动，正在第 $retry/3 次重试。",
                                    ),
                                )
                            }
                        },
                    )
                }
                withContext(Dispatchers.IO) { connectedComputerStore.saveConnection(connection) }
                val checkedAt = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        connectedComputer = connection.toSafeComputer(),
                        desktopLastCheckedAtMillis = checkedAt,
                        desktopLastCheckSucceeded = true,
                        desktopTransfer = DesktopTransferUiState(
                            phase = DesktopTransferPhase.COMPLETED,
                            message = "电脑已连接 · 等待文件",
                        ),
                        status = "已连接 ${connection.receiverName}，可将 Excel 发送到该电脑。",
                        error = null,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val message = readableDesktopError(error, endpoint)
                _state.update {
                    it.copy(
                        desktopLastCheckedAtMillis = System.currentTimeMillis(),
                        desktopLastCheckSucceeded = false,
                        desktopTransfer = DesktopTransferUiState(
                            phase = DesktopTransferPhase.FAILED,
                            message = message,
                        ),
                        error = "连接电脑出现问题：$message",
                    )
                }
            }
        }
    }

    fun reportDesktopScannerPermissionDenied() {
        _state.update {
            it.copy(error = "请允许相机权限后扫描电脑端二维码，或粘贴完整连接信息。")
        }
    }

    fun checkConnectedComputer() {
        if (_state.value.desktopTransfer.isWorking) return
        viewModelScope.launch {
            val connection = withContext(Dispatchers.IO) { connectedComputerStore.loadConnection() }
            if (connection == null) {
                _state.update { it.copy(status = "尚未连接电脑，请扫描桌面接收器二维码。", error = null) }
                return@launch
            }
            _state.update {
                it.copy(
                    desktopTransfer = DesktopTransferUiState(
                        phase = DesktopTransferPhase.CHECKING,
                        message = "正在检测 ${connection.receiverName}。",
                    ),
                    error = null,
                )
            }
            try {
                val health = withContext(Dispatchers.IO) {
                    desktopReceiverClient.health(connection) { retry, _ ->
                        _state.update {
                            it.copy(
                                desktopTransfer = it.desktopTransfer.copy(
                                    message = "电脑响应较慢，正在第 $retry/3 次检测。",
                                ),
                            )
                        }
                    }
                }
                if (health.receiverId != connection.receiverId || health.status.lowercase() != "ready") {
                    throw DesktopReceiverException.protocol("桌面接收器身份或状态校验未通过")
                }
                _state.update {
                    it.copy(
                        connectedComputer = connection.toSafeComputer(),
                        desktopLastCheckedAtMillis = System.currentTimeMillis(),
                        desktopLastCheckSucceeded = true,
                        desktopTransfer = DesktopTransferUiState(),
                        status = "${connection.receiverName} 已就绪 · 等待文件",
                        error = null,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val message = readableDesktopError(error, "${connection.host}:${connection.port}")
                _state.update {
                    it.copy(
                        desktopLastCheckedAtMillis = System.currentTimeMillis(),
                        desktopLastCheckSucceeded = false,
                        desktopTransfer = DesktopTransferUiState(
                            phase = DesktopTransferPhase.FAILED,
                            message = message,
                        ),
                        error = "电脑连通性检测出现问题：$message",
                    )
                }
            }
        }
    }

    fun disconnectComputer() {
        desktopUploadJob?.cancel()
        desktopUploadGeneration.incrementAndGet()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { connectedComputerStore.disconnect() }
            _state.update {
                it.copy(
                    connectedComputer = null,
                    desktopLastCheckedAtMillis = null,
                    desktopLastCheckSucceeded = null,
                    desktopTransfer = DesktopTransferUiState(),
                    status = "已解除桌面接收器连接。",
                    error = null,
                )
            }
        }
    }

    fun sendResultToDesktop() {
        if (_state.value.desktopTransfer.isWorking) return
        viewModelScope.launch {
            try {
                val snapshot = _state.value
                val taskId = snapshot.activeTaskId ?: "TASK-" + System.currentTimeMillis()
                val uri = snapshot.resultUri ?: persistCurrentResult()
                persistHistoryResult(uri, taskId)
                _state.update { current ->
                    if (current.resultUri == uri) current else current.copy(resultUri = uri)
                }
                startDesktopUpload(taskId, uri, snapshot.resultName)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { it.copy(error = "准备电脑交付文件出现问题：${readableError(error)}") }
            }
        }
    }

    fun sendHistoryToDesktop(item: TaskHistoryItem) {
        if (_state.value.desktopTransfer.isWorking) return
        val uri = resolveHistoryResultUri(item)
        if (uri == null) {
            _state.update { it.copy(status = "该历史任务没有可发送的本地 Excel 文件。", error = null) }
            return
        }
        persistRecoveredHistoryUri(item, uri)
        startDesktopUpload(item.taskId, uri, item.resultName)
    }

    /** Stops the current coroutine but retains the acknowledged chunk checkpoint for a later resend. */
    fun pauseDesktopTransfer() {
        val current = _state.value.desktopTransfer
        if (current.phase != DesktopTransferPhase.SENDING) return
        desktopUploadJob?.cancel()
        desktopUploadGeneration.incrementAndGet()
        _state.update {
            it.copy(
                desktopTransfer = current.copy(
                    phase = DesktopTransferPhase.PAUSED,
                    message = "已暂停发送，稍后点击发送可从已确认分片继续。",
                ),
                status = "电脑交付已暂停，手机端 Excel 保持可用。",
                error = null,
            )
        }
    }

    private fun startDesktopUpload(taskId: String, uri: Uri, preferredName: String?) {
        if (desktopUploadJob?.isActive == true || _state.value.desktopTransfer.isWorking) return
        val generation = desktopUploadGeneration.incrementAndGet()
        desktopUploadJob = viewModelScope.launch {
            val connection = withContext(Dispatchers.IO) { connectedComputerStore.loadConnection() }
            if (generation != desktopUploadGeneration.get()) return@launch
            if (connection == null) {
                _state.update { it.copy(error = "请先在设置中连接电脑，再发送 Excel 文件。") }
                return@launch
            }
            val fileInfo = withContext(Dispatchers.IO) { FileResultStore.fileInfo(getApplication(), uri) }
            if (generation != desktopUploadGeneration.get()) return@launch
            if (fileInfo == null || fileInfo.sizeBytes <= 0L || !FileResultStore.canRead(getApplication(), uri)) {
                _state.update {
                    it.copy(error = "本地 Excel 文件已不存在或无法读取，请重新生成后再发送。")
                }
                return@launch
            }
            val fileName = preferredName?.trim().takeUnless { it.isNullOrBlank() } ?: fileInfo.displayName
            _state.update { current ->
                if (generation != desktopUploadGeneration.get()) {
                    current
                } else {
                    current.copy(
                        desktopTransfer = DesktopTransferUiState(
                            phase = DesktopTransferPhase.SENDING,
                            taskId = taskId,
                            fileName = fileName,
                            totalBytes = fileInfo.sizeBytes,
                            message = "正在准备发送到 ${connection.receiverName}。",
                        ),
                        error = null,
                    )
                }
            }
            if (generation != desktopUploadGeneration.get()) return@launch
            try {
                val receipt = desktopReceiverClient.upload(
                    connection = connection,
                    source = ReceiverUploadSource(
                        fileName = fileName,
                        totalBytes = fileInfo.sizeBytes,
                        openStream = { FileResultStore.openInputStream(getApplication(), uri) },
                    ),
                    taskId = taskId,
                    sourceUri = uri.toString(),
                    checkpoint = withContext(Dispatchers.IO) { connectedComputerStore.loadCheckpoint(taskId) },
                    onCheckpoint = { checkpoint ->
                        withContext(Dispatchers.IO) {
                            connectedComputerStore.saveCheckpoint(checkpoint.copy(sourceUri = uri.toString()))
                        }
                    },
                    onProgress = { progress ->
                        updateDesktopUploadProgress(taskId, fileName, progress, generation)
                    },
                    onRetry = { retry, _ ->
                        if (generation == desktopUploadGeneration.get()) {
                            _state.update { current ->
                                if (
                                    generation != desktopUploadGeneration.get() ||
                                    current.desktopTransfer.taskId != taskId
                                ) {
                                    current
                                } else {
                                    current.copy(
                                        desktopTransfer = current.desktopTransfer.copy(
                                            message = "网络波动，正在第 $retry/3 次重试并续传。",
                                        ),
                                    )
                                }
                            }
                        }
                    },
                )
                withContext(Dispatchers.IO) { connectedComputerStore.clearCheckpoint(taskId) }
                val delivery = receipt.toHistoryDelivery(connection.receiverId, connection.receiverName)
                updateHistoryDelivery(taskId, delivery)
                if (generation != desktopUploadGeneration.get()) return@launch
                _state.update {
                    it.copy(
                        desktopTransfer = DesktopTransferUiState(
                            phase = DesktopTransferPhase.COMPLETED,
                            taskId = taskId,
                            fileName = receipt.fileName,
                            progress = 1f,
                            sentBytes = receipt.size,
                            totalBytes = receipt.size,
                            message = "电脑已接收 · ${receipt.savedPath}",
                            receipt = receipt,
                        ),
                        status = "电脑已接收 · 已保存到 ${receipt.savedPath}",
                        error = null,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) return@launch
                if (generation != desktopUploadGeneration.get()) return@launch
                if (error is DesktopReceiverException && error.type == DesktopReceiverErrorType.UNAUTHORIZED) {
                    withContext(Dispatchers.IO) { connectedComputerStore.disconnect() }
                }
                val message = readableDesktopError(error, "${connection.host}:${connection.port}")
                _state.update {
                    it.copy(
                        connectedComputer = if (error is DesktopReceiverException && error.type == DesktopReceiverErrorType.UNAUTHORIZED) null else it.connectedComputer,
                        desktopTransfer = it.desktopTransfer.copy(
                            phase = DesktopTransferPhase.FAILED,
                            message = message,
                        ),
                        error = "发送到电脑出现问题：$message",
                    )
                }
            }
        }
    }

    private fun updateDesktopUploadProgress(
        taskId: String,
        fileName: String,
        progress: ReceiverUploadProgress,
        generation: Long,
    ) {
        if (generation != desktopUploadGeneration.get()) return
        _state.update {
            if (generation != desktopUploadGeneration.get()) return@update it
            it.copy(
                desktopTransfer = DesktopTransferUiState(
                    phase = DesktopTransferPhase.SENDING,
                    taskId = taskId,
                    fileName = fileName,
                    progress = progress.fraction.coerceIn(0f, 1f),
                    sentBytes = progress.sentBytes,
                    totalBytes = progress.totalBytes,
                    completedChunks = progress.completedChunks,
                    totalChunks = progress.totalChunks,
                    message = "正在发送 · ${(progress.fraction * 100).toInt()}% · 分片 ${progress.completedChunks}/${progress.totalChunks}",
                ),
            )
        }
    }

    private fun retryImages(imageIds: Set<String>) {
        val snapshot = _state.value
        val inputs = snapshot.images.filter { it.id in imageIds }
        if (inputs.isEmpty()) {
            _state.update { it.copy(status = "当前会话没有可重新识别的原图，请重新拍摄后建立新任务。", error = null) }
            return
        }
        startTask(
            inputs = inputs,
            taskId = snapshot.activeTaskId,
            startedAtMillis = snapshot.taskStartedAtMillis,
            priorOutcomes = snapshot.imageOutcomes,
        )
    }

    private fun startTask(
        inputs: List<SelectedImage>,
        taskId: String? = null,
        startedAtMillis: Long? = null,
        priorOutcomes: List<ImageOutcome> = emptyList(),
    ) {
        val snapshot = _state.value
        if (snapshot.isRunning || snapshot.isTestingConnection) return
        if (inputs.isEmpty()) {
            _state.update { it.copy(error = "请先拍照或选择至少一张完整机柜图片。") }
            return
        }
        val config = snapshot.toConfig()
        val prompt = snapshot.activePrompt ?: defaultPrompt()
        val taskStartedAt = startedAtMillis ?: System.currentTimeMillis()
        val currentTaskId = taskId ?: "TASK-" + taskStartedAt
        val requested = snapshot.concurrency.coerceIn(1, TaskPolicy.MAX_CONCURRENCY)
        val effective = TaskPolicy.effectiveConcurrency(requested, inputs.size)
        val priorById = priorOutcomes.associateBy(ImageOutcome::imageId)
        if (priorOutcomes.isNotEmpty()) {
            invalidateCurrentDelivery(snapshot.activeTaskId)
        }
        val retriedIds = inputs.mapTo(mutableSetOf(), SelectedImage::id)
        val pending = if (priorOutcomes.isEmpty()) {
            inputs.map { image ->
                ImageOutcome(
                    imageName = image.name,
                    rack = null,
                    state = ImageProcessingState.QUEUED,
                    message = "等待识别引擎分配任务。",
                    imageId = image.id,
                )
            }
        } else {
            priorOutcomes.map { outcome ->
                if (outcome.imageId in retriedIds) outcome.copy(
                    state = ImageProcessingState.QUEUED,
                    retryCount = 0,
                    message = "正在重新识别此图，已确认机柜会保留在本次任务中。",
                    repairEvents = emptyList(),
                    reviewConfirmed = false,
                ) else outcome
            }
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRunning = true,
                    progress = 0f,
                    exportPhase = ExportPhase.IDLE,
                    exportProgress = 0f,
                    exportEllipsisCount = 0,
                    exportTargetMillis = 0L,
                    exportActualWriteMillis = null,
                    efficiency = TaskEfficiencyMetrics.EMPTY,
                    confirmationMode = "未确认",
                    status = if (priorOutcomes.isEmpty()) {
                        "智能识别任务已启动 · 并发 $effective · 共 ${inputs.size} 张图片"
                    } else {
                        "正在增量重新识别 ${inputs.size} 张图片，已确认机柜将保留。"
                    },
                    error = null,
                    racks = if (priorOutcomes.isEmpty()) emptyList() else it.racks,
                    imageOutcomes = pending,
                    activeTaskId = currentTaskId,
                    taskStartedAtMillis = taskStartedAt,
                    taskFinishedAtMillis = null,
                    recognitionFinishedAtMillis = null,
                    reviewStartedAtMillis = null,
                    resultUri = null,
                    resultName = null,
                )
            }
            runCatching { withContext(Dispatchers.IO) { configStore.saveProfiles(snapshot.toProfileCollection()) } }
                .onFailure { error ->
                    _state.update { it.copy(status = "模型配置已用于本次任务；本机保存提示：" + readableError(error)) }
                }

            val archives = withContext(Dispatchers.IO) {
                inputs.associate { image ->
                    val archive = runCatching {
                        taskArchiveStore.archive(currentTaskId, image.id, image.uri, snapshot.retainOriginalImages)
                    }.getOrNull()
                    image.id to archive
                }
            }
            val processed = AtomicInteger(0)
            val semaphore = Semaphore(effective)
            val processedOutcomes = supervisorScope {
                inputs.map { image ->
                    async {
                        semaphore.withPermit {
                            val archivedPath = archives[image.id]?.reviewImagePath ?: priorById[image.id]?.reviewImagePath
                            updateOutcome(
                                image.id,
                                ImageOutcome(
                                    imageName = image.name,
                                    rack = priorById[image.id]?.rack,
                                    state = ImageProcessingState.ANALYZING,
                                    message = "正在识别设备前面板、U 位数字和柜号。",
                                    imageId = image.id,
                                    reviewImagePath = archivedPath,
                                ),
                            )
                            val repairs = mutableListOf<RepairEvent>()
                            val result = retryExecutor.attempt(
                                onRetry = { retry, error ->
                                    val event = RepairEvent(retry, readableError(error))
                                    repairs += event
                                    updateOutcome(
                                        image.id,
                                        ImageOutcome(
                                            imageName = image.name,
                                            rack = priorById[image.id]?.rack,
                                            state = ImageProcessingState.REPAIRING,
                                            retryCount = retry,
                                            message = TaskPolicy.repairStatus(retry) + "：" + event.message,
                                            repairEvents = repairs.toList(),
                                            imageId = image.id,
                                            reviewImagePath = archivedPath,
                                        ),
                                    )
                                    _state.update { state -> state.copy(status = TaskPolicy.repairStatus(retry) + " · " + image.name) }
                                },
                            ) {
                                val prepared = withContext(Dispatchers.IO) {
                                    ImagePreprocessor.prepare(getApplication(), image.uri)
                                }
                                apiClient.analyze(config, prompt.content, prepared, image.name)
                            }
                            val outcome = result.value?.let { rack ->
                                val needsReview = ReviewPolicy.requiresManualReview(rack)
                                val reviewCount = rack.uncertain.size
                                ImageOutcome(
                                    imageName = image.name,
                                    rack = rack,
                                    state = if (needsReview) ImageProcessingState.QUALITY_REVIEW else ImageProcessingState.COMPLETED,
                                    retryCount = result.retryCount,
                                    message = when {
                                        needsReview -> "识别完成，发现 $reviewCount 项待人工复核。"
                                        result.retryCount == 0 -> "识别完成。"
                                        else -> "识别完成，已完成自动修复。"
                                    },
                                    repairEvents = repairs.toList(),
                                    imageId = image.id,
                                    reviewImagePath = archivedPath,
                                )
                            } ?: ImageOutcome(
                                imageName = image.name,
                                rack = null,
                                state = ImageProcessingState.FAILED,
                                retryCount = result.retryCount,
                                message = TaskPolicy.repairStatus(TaskPolicy.MAX_RETRIES + 1) + "：" + readableError(result.error),
                                repairEvents = repairs.toList(),
                                imageId = image.id,
                                reviewImagePath = archivedPath,
                            )
                            updateOutcome(image.id, outcome)
                            val completed = processed.incrementAndGet()
                            _state.update { state ->
                                state.copy(
                                    progress = completed.toFloat() / inputs.size.toFloat(),
                                    status = "已完成 $completed/${inputs.size} 张图片 · 并发 $effective",
                                )
                            }
                            outcome
                        }
                    }
                }.awaitAll()
            }

            val merged = if (priorOutcomes.isEmpty()) {
                processedOutcomes
            } else {
                TaskMergePolicy.replaceByKey(priorOutcomes, processedOutcomes, ImageOutcome::imageId)
            }
            val finalOutcomes = normalizeTaskOutcomes(merged)
            val recognitionFinishedAt = System.currentTimeMillis()
            val needsReview = finalOutcomes.any(ReviewPolicy::requiresManualReview)
            _state.update { current ->
                val recognizedRacks = RackOrdering.sort(finalOutcomes.mapNotNull(ImageOutcome::rack)).racks
                current.copy(
                    isRunning = false,
                    progress = 1f,
                    imageOutcomes = finalOutcomes,
                    racks = recognizedRacks,
                    recognitionFinishedAtMillis = recognitionFinishedAt,
                    reviewStartedAtMillis = null,
                    exportPhase = ExportPhase.WAITING_CONFIRMATION,
                    exportProgress = 0f,
                    exportEllipsisCount = 0,
                    status = if (needsReview) {
                        "智能识别已完成，存在待复核项；可进入复核，也可按当前结果确认生成 Excel。"
                    } else {
                        "智能识别已完成，请确认无疑义后生成 Excel。"
                    },
                    error = null,
                )
            }
            if (_state.value.alarmSettings.demoMode) {
                seedDemoAlarmsForCurrentTask()
            }
        }
    }

    private fun startExportFromCurrentState(mode: String) {
        val snapshot = _state.value
        if (snapshot.resultName != null || snapshot.exportPhase == ExportPhase.COMPLETED) {
            _state.update {
                it.copy(
                    status = "本次三表 Excel 已交付。请点击开始新一轮采集，不会重复生成同一任务。",
                    error = null,
                )
            }
            return
        }
        if (snapshot.isRunning || snapshot.exportPhase == ExportPhase.WRITING) return
        val outcomes = snapshot.imageOutcomes
        if (outcomes.isEmpty() || outcomes.none { it.rack != null }) {
            _state.update { it.copy(status = "当前没有可交付的识别结果。", error = null) }
            return
        }
        val taskStartedAt = snapshot.taskStartedAtMillis ?: System.currentTimeMillis()
        val taskId = snapshot.activeTaskId ?: "TASK-$taskStartedAt"
        val requested = snapshot.concurrency.coerceIn(1, TaskPolicy.MAX_CONCURRENCY)
        val effective = TaskPolicy.effectiveConcurrency(requested, outcomes.size.coerceAtLeast(1))
        val exportRacks = RackOrdering.sort(outcomes.mapNotNull(ImageOutcome::rack)).racks
        val targetMillis = ExportHoldPolicy.chooseTargetMillis(
            imageCount = snapshot.images.size.coerceAtLeast(outcomes.size),
            cabinetCount = exportRacks.size,
            deviceCount = exportRacks.sumOf { it.devices.size },
            reviewCount = outcomes.count(ReviewPolicy::requiresManualReview),
        )
        // Freeze the review clock before the Excel delivery phase begins.
        // The delivery feedback interval must not count as operator review time.
        val deliveryConfirmedAtMillis = System.currentTimeMillis()
        _state.update {
            it.copy(
                isRunning = true,
                exportPhase = ExportPhase.WRITING,
                exportProgress = 0f,
                exportEllipsisCount = 1,
                exportTargetMillis = targetMillis,
                exportActualWriteMillis = null,
                confirmationMode = mode,
                status = "AI 已完成识别，正在按确认结果生成并校验三张 Excel 交付表，请稍候。",
                error = null,
            )
        }
        viewModelScope.launch {
            completeExport(
                snapshot = _state.value,
                taskId = taskId,
                taskStartedAt = taskStartedAt,
                config = snapshot.toConfig(),
                prompt = snapshot.activePrompt ?: defaultPrompt(),
                requested = requested,
                effective = effective,
                outcomes = outcomes,
                confirmationMode = mode,
                deliveryConfirmedAtMillis = deliveryConfirmedAtMillis,
            )
        }
    }

    private fun updateOutcome(imageId: String, outcome: ImageOutcome) {
        _state.update { current ->
            current.copy(imageOutcomes = current.imageOutcomes.map { item ->
                if (item.imageId == imageId) outcome else item
            })
        }
    }

    /**
     * Duplicated cabinet labels are never silently chosen for the field view.
     * RackOrdering still selects the strongest candidate for export, while both
     * source photos remain visible in the manual-review queue.
     */
    private fun normalizeTaskOutcomes(outcomes: List<ImageOutcome>): List<ImageOutcome> {
        val duplicatedIds = outcomes.mapNotNull(ImageOutcome::cabinetId)
            .map { RackOrdering.normalize(it) ?: it.trim().uppercase() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicatedIds.isEmpty()) return outcomes
        return outcomes.map { outcome ->
            val rack = outcome.rack ?: return@map outcome
            val normalizedId = RackOrdering.normalize(rack.cabinetId) ?: rack.cabinetId.trim().uppercase()
            if (normalizedId !in duplicatedIds) return@map outcome
            val note = "柜号 $normalizedId 在本任务中出现多次，请现场确认对应照片。"
            outcome.copy(
                rack = rack.copy(uncertain = (rack.uncertain + note).distinct()),
                state = ImageProcessingState.QUALITY_REVIEW,
                reviewConfirmed = false,
                message = "识别到重复柜号，已转入现场复核。",
            )
        }
    }

    /**
     * A layout edit or retry changes workbook inputs. Remove the delivery
     * pointer before generating the replacement so an older Excel file cannot
     * be opened, shared, or sent as the current task result.
     */
    private fun invalidateCurrentDelivery(taskId: String?) {
        lastBytes = null
        if (taskId == null) return
        val item = historyStore.load().firstOrNull { it.taskId == taskId } ?: return
        if (item.resultUri == null && item.resultName == null && item.desktopDelivery == null) return
        historyStore.add(
            item.copy(
                resultUri = null,
                resultName = null,
                desktopDelivery = null,
            ),
        )
        _state.update { current ->
            if (current.activeTaskId == taskId) {
                current.copy(
                    resultUri = null,
                    resultName = null,
                    taskFinishedAtMillis = null,
                    exportPhase = if (current.imageOutcomes.isNotEmpty()) ExportPhase.WAITING_CONFIRMATION else ExportPhase.IDLE,
                    exportProgress = 0f,
                    exportEllipsisCount = 0,
                    exportTargetMillis = 0L,
                    exportActualWriteMillis = null,
                    efficiency = TaskEfficiencyMetrics.EMPTY,
                    confirmationMode = "未确认",
                    taskHistory = historyStore.load(),
                )
            } else {
                current
            }
        }
    }

    private fun applyLocalReviewUpdate(
        snapshot: RackExcelUiState,
        updated: ImageOutcome,
        status: String,
    ) {
        val outcomes = normalizeTaskOutcomes(
            TaskMergePolicy.replaceByKey(snapshot.imageOutcomes, listOf(updated), ImageOutcome::imageId),
        )
        val racks = RackOrdering.sort(outcomes.mapNotNull(ImageOutcome::rack)).racks
        invalidateCurrentDelivery(snapshot.activeTaskId)
        _state.update {
            it.copy(
                imageOutcomes = outcomes,
                racks = racks,
                resultUri = null,
                resultName = null,
                taskFinishedAtMillis = null,
                reviewStartedAtMillis = snapshot.reviewStartedAtMillis ?: System.currentTimeMillis(),
                exportPhase = ExportPhase.WAITING_CONFIRMATION,
                exportProgress = 0f,
                exportEllipsisCount = 0,
                exportTargetMillis = 0L,
                exportActualWriteMillis = null,
                efficiency = TaskEfficiencyMetrics.EMPTY,
                confirmationMode = "未确认",
                status = status,
                error = null,
            )
        }
        refreshAlarmMatches()
    }

    private suspend fun completeExport(
        snapshot: RackExcelUiState,
        taskId: String,
        taskStartedAt: Long,
        config: ModelConfig,
        prompt: PromptTemplate,
        requested: Int,
        effective: Int,
        outcomes: List<ImageOutcome>,
        confirmationMode: String,
        deliveryConfirmedAtMillis: Long,
    ) {
        val ordering = RackOrdering.sort(outcomes.mapNotNull(ImageOutcome::rack))
        val failed = outcomes.filter { it.state == ImageProcessingState.FAILED }
            .map { "${it.imageName}：${it.message.ifBlank { "修复失败，建议补拍" }}" }
        val exportTask = ExportTask(
            taskId = taskId,
            roomName = snapshot.roomName.trim(),
            createdAtMillis = taskStartedAt,
            modelName = config.model,
            promptName = prompt.name,
            promptVersion = prompt.version,
            promptContent = prompt.content,
            requestedConcurrency = requested,
            effectiveConcurrency = effective,
            outcomes = outcomes,
            racks = ordering.racks,
            analysis = RiskAnalysisEngine.analyze(ordering.racks, failed + ordering.warnings),
        )
        val filename = ExportFileName.build(
            prefix = snapshot.filePrefix,
            cabinetIds = ordering.racks.map(Rack::cabinetId),
            nowMillis = System.currentTimeMillis(),
            roomName = exportTask.roomName,
        )
        try {
            val targetMillis = snapshot.exportTargetMillis.takeIf(ExportHoldPolicy::isSupportedTarget)
                ?: ExportHoldPolicy.chooseTargetMillis(
                    imageCount = snapshot.images.size.coerceAtLeast(outcomes.size),
                    cabinetCount = ordering.racks.size,
                    deviceCount = ordering.racks.sumOf { it.devices.size },
                    reviewCount = outcomes.count(ReviewPolicy::requiresManualReview),
                )
            val writeStartedAt = System.currentTimeMillis()
            var ellipsis = 1
            val ticker = viewModelScope.launch {
                while (isActive) {
                    _state.update { current ->
                        if (current.activeTaskId != taskId || current.exportPhase != ExportPhase.WRITING) {
                            current
                        } else {
                            current.copy(
                                exportEllipsisCount = ellipsis,
                                exportProgress = ((System.currentTimeMillis() - writeStartedAt).toFloat() /
                                    targetMillis.toFloat()).coerceIn(0f, 0.94f),
                                status = "AI 已完成识别，正在按确认结果生成并校验三张 Excel 交付表，请稍候" + ".".repeat(ellipsis),
                            )
                        }
                    }
                    ellipsis = if (ellipsis >= 6) 1 else ellipsis + 1
                    delay(280L)
                }
            }
            val bytes: ByteArray
            val resultUri: Uri
            val downloadError: Throwable?
            try {
                bytes = withContext(Dispatchers.IO) {
                    val template = getApplication<Application>().assets.open("机柜信息登记图模板.xlsx").use { it.readBytes() }
                    XlsxWriter.bytes(exportTask, template)
                }
                // The private result is the canonical task file. It survives a
                // process restart even when the user has disabled automatic saves
                // to the public Downloads directory.
                resultUri = withContext(Dispatchers.IO) {
                    FileResultStore.savePrivate(getApplication(), bytes, filename)
                }
                downloadError = if (snapshot.autoSave) {
                    try {
                        withContext(Dispatchers.IO) { FileResultStore.save(getApplication(), bytes, filename) }
                        null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        error
                    }
                } else {
                    null
                }
                // Persist a durable recovery point before the visible delivery
                // interval. If Android recreates the process during that interval,
                // the existing workbook is restored instead of being generated again.
                val provisionalFinishedAt = System.currentTimeMillis()
                val provisionalEfficiency = TaskEfficiencyMetrics.calculate(
                    imageCount = snapshot.images.size.coerceAtLeast(outcomes.size),
                    cabinetCount = ordering.racks.size,
                    aiRecognitionMillis = (snapshot.recognitionFinishedAtMillis ?: provisionalFinishedAt) - taskStartedAt,
                    manualReviewMillis = TaskEfficiencyMetrics.manualReviewDurationMillis(
                        reviewStartedAtMillis = snapshot.reviewStartedAtMillis,
                        deliveryConfirmedAtMillis = deliveryConfirmedAtMillis,
                    ),
                    automatedExportMillis = targetMillis,
                )
                val provisionalHistory = buildHistoryItem(
                    task = exportTask,
                    startedAt = taskStartedAt,
                    finishedAt = provisionalFinishedAt,
                    resultName = filename,
                    resultUri = resultUri,
                    efficiency = provisionalEfficiency,
                    confirmationMode = confirmationMode,
                )
                withContext(Dispatchers.IO) { historyStore.add(provisionalHistory) }
            } finally {
                ticker.cancelAndJoin()
            }
            val actualWriteMillis = (System.currentTimeMillis() - writeStartedAt).coerceAtLeast(0L)
            val holdMillis = maxOf(targetMillis, actualWriteMillis)
            var held = actualWriteMillis
            while (held < holdMillis) {
                val slice = minOf(280L, holdMillis - held)
                delay(slice)
                held += slice
                _state.update { current ->
                    if (current.activeTaskId != taskId || current.exportPhase != ExportPhase.WRITING) {
                        current
                    } else {
                        current.copy(
                            exportEllipsisCount = ellipsis,
                            exportProgress = (held.toFloat() / holdMillis.toFloat()).coerceIn(0f, 0.98f),
                            status = "AI 已完成识别，正在按确认结果生成并校验三张 Excel 交付表，请稍候" + ".".repeat(ellipsis),
                        )
                    }
                }
                ellipsis = if (ellipsis >= 6) 1 else ellipsis + 1
            }
            lastBytes = bytes
            val finishedAt = System.currentTimeMillis()
            val efficiency = TaskEfficiencyMetrics.calculate(
                imageCount = snapshot.images.size.coerceAtLeast(outcomes.size),
                cabinetCount = ordering.racks.size,
                aiRecognitionMillis = (snapshot.recognitionFinishedAtMillis ?: finishedAt) - taskStartedAt,
                manualReviewMillis = TaskEfficiencyMetrics.manualReviewDurationMillis(
                    reviewStartedAtMillis = snapshot.reviewStartedAtMillis,
                    deliveryConfirmedAtMillis = deliveryConfirmedAtMillis,
                ),
                automatedExportMillis = holdMillis,
            )
            val history = buildHistoryItem(
                task = exportTask,
                startedAt = taskStartedAt,
                finishedAt = finishedAt,
                resultName = filename,
                resultUri = resultUri,
                efficiency = efficiency,
                confirmationMode = confirmationMode,
            )
            withContext(Dispatchers.IO) { historyStore.add(history) }
            _state.update {
                it.copy(
                    isRunning = false,
                    progress = 1f,
                    exportPhase = ExportPhase.COMPLETED,
                    exportProgress = 1f,
                    exportEllipsisCount = 6,
                    exportTargetMillis = targetMillis,
                    exportActualWriteMillis = actualWriteMillis,
                    efficiency = efficiency,
                    confirmationMode = confirmationMode,
                    status = when {
                        downloadError == null && snapshot.autoSave -> "三表 Excel 已生成并保存到下载/云枢智维。"
                        downloadError != null -> "三表 Excel 已生成并保存在应用内；自动保存到下载目录出现问题：${readableError(downloadError)}"
                        else -> "三表 Excel 已生成并保存在应用内，可打开、分享或发送到电脑。"
                    },
                    error = null,
                    racks = ordering.racks,
                    imageOutcomes = outcomes,
                    activeTaskId = taskId,
                    taskStartedAtMillis = taskStartedAt,
                    taskFinishedAtMillis = finishedAt,
                    resultUri = resultUri,
                    resultName = filename,
                    taskHistory = historyStore.load(),
                )
            }
        } catch (error: Throwable) {
            val finishedAt = System.currentTimeMillis()
            val history = buildHistoryItem(
                task = exportTask,
                startedAt = taskStartedAt,
                finishedAt = finishedAt,
                resultName = null,
                resultUri = null,
                efficiency = TaskEfficiencyMetrics.EMPTY,
                confirmationMode = confirmationMode,
            )
            withContext(Dispatchers.IO) { historyStore.add(history) }
            _state.update {
                it.copy(
                    isRunning = false,
                    exportPhase = ExportPhase.FAILED,
                    exportProgress = 0f,
                    exportActualWriteMillis = null,
                    taskFinishedAtMillis = finishedAt,
                    racks = ordering.racks,
                    imageOutcomes = outcomes,
                    resultUri = null,
                    resultName = null,
                    error = "生成 Excel 出现问题：${readableError(error)}",
                    taskHistory = historyStore.load(),
                )
            }
        }
    }

    private fun buildHistoryItem(
        task: ExportTask,
        startedAt: Long,
        finishedAt: Long,
        resultName: String?,
        resultUri: Uri?,
        efficiency: TaskEfficiencyMetrics = TaskEfficiencyMetrics.EMPTY,
        confirmationMode: String = "未确认",
    ): TaskHistoryItem = TaskHistoryItem(
        taskId = task.taskId,
        roomName = task.roomName,
        createdAtMillis = startedAt,
        startedAtMillis = startedAt,
        finishedAtMillis = finishedAt,
        cabinetIds = task.racks.map(Rack::cabinetId),
        summary = task.summary,
        resultName = resultName,
        resultUri = resultUri?.toString(),
        efficiency = efficiency,
        confirmationMode = confirmationMode,
        recognitionFinishedAtMillis = _state.value.recognitionFinishedAtMillis,
        reviewStartedAtMillis = _state.value.reviewStartedAtMillis,
        reviewItems = task.outcomes.map { outcome ->
            TaskReviewSnapshot(
                imageId = outcome.imageId,
                imageName = outcome.imageName,
                cabinetId = outcome.cabinetId,
                reviewImagePath = outcome.reviewImagePath,
                state = outcome.state.label,
                reviewConfirmed = outcome.reviewConfirmed,
                message = outcome.message,
                retryCount = outcome.retryCount,
                repairEvents = outcome.repairEvents.map { event -> "第 ${event.retry} 次：${event.message}" },
                uncertain = outcome.rack?.uncertain.orEmpty(),
                riskTexts = outcome.rack?.riskCandidates.orEmpty().map { risk -> "${risk.category}：${risk.description}" },
                rack = outcome.rack,
            )
        },
        alarmRecords = _state.value.alarms.filter { it.taskId == task.taskId },
    )

    private suspend fun persistHistoryResult(uri: Uri, explicitTaskId: String? = null) {
        val snapshot = _state.value
        val taskId = explicitTaskId ?: snapshot.activeTaskId ?: return
        val item = withContext(Dispatchers.IO) {
            historyStore.load().firstOrNull { it.taskId == taskId }
        } ?: return
        val updated = item.copy(resultName = snapshot.resultName ?: item.resultName, resultUri = uri.toString())
        withContext(Dispatchers.IO) { historyStore.add(updated) }
        _state.update { it.copy(taskHistory = historyStore.load()) }
    }

    /**
     * Older history rows may predate resultUri persistence. Recover the
     * canonical app-private workbook by its recorded name when it is still on
     * the device, while keeping malformed/stale URI values from crashing the
     * detail page.
     */
    private fun resolveHistoryResultUri(item: TaskHistoryItem): Uri? =
        item.resultUri
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
            ?: FileResultStore.findPrivate(getApplication(), item.resultName)

    private fun persistRecoveredHistoryUri(item: TaskHistoryItem, uri: Uri) {
        if (item.resultUri == uri.toString()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                historyStore.add(item.copy(resultUri = uri.toString()))
            }
            _state.update { current ->
                current.copy(
                    taskHistory = historyStore.load(),
                )
            }
        }
    }

    private fun preservePreviousDeliveryThenAddImages(
        snapshot: RackExcelUiState,
        items: List<SelectedImage>,
    ) {
        val bytes = lastBytes ?: return
        val resultName = snapshot.resultName ?: return
        val taskId = snapshot.activeTaskId
        isPreservingPreviousDelivery = true
        _state.update {
            it.copy(
                status = "正在保留上一轮 Excel，完成后将自动开始新的现场采集任务。",
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    FileResultStore.savePrivate(getApplication(), bytes, resultName)
                }
                persistHistoryResult(uri, taskId)
                _state.update { current ->
                    if (current.activeTaskId == taskId) current.copy(resultUri = uri) else current
                }
                isPreservingPreviousDelivery = false
                addImages(items)
            } catch (error: Throwable) {
                isPreservingPreviousDelivery = false
                if (error is CancellationException) throw error
                _state.update {
                    it.copy(error = "保留上一轮 Excel 出现问题：${readableError(error)}")
                }
            }
        }
    }

    private suspend fun updateHistoryDelivery(taskId: String, delivery: DesktopDeliveryReceipt) {
        val item = withContext(Dispatchers.IO) {
            historyStore.load().firstOrNull { it.taskId == taskId }
        } ?: return
        withContext(Dispatchers.IO) { historyStore.add(item.copy(desktopDelivery = delivery)) }
        _state.update { it.copy(taskHistory = historyStore.load()) }
    }

    private suspend fun persistCurrentResult(): Uri {
        val snapshot = _state.value
        val bytes = readCurrentResultBytes(snapshot)
        val name = snapshot.resultName ?: "云枢智维_识别结果.xlsx"
        return withContext(Dispatchers.IO) { FileResultStore.savePrivate(getApplication(), bytes, name) }
    }

    private suspend fun readCurrentResultBytes(snapshot: RackExcelUiState): ByteArray {
        lastBytes?.let { return it }
        val uri = snapshot.resultUri ?: error("当前会话暂无可保存的 Excel 文件。")
        return withContext(Dispatchers.IO) {
            FileResultStore.openInputStream(getApplication(), uri).use { input -> input.readBytes() }
        }
    }

    private fun setPromptState(template: PromptTemplate, status: String) {
        _state.update {
            it.copy(
                promptTemplates = promptStore.templates(),
                activePromptId = template.id,
                promptEditorId = template.id,
                promptEditorName = template.name,
                promptEditorVersion = template.version,
                promptEditorContent = template.content,
                status = status,
                error = null,
            )
        }
    }

    private suspend fun loadAlarmRecords(showStatus: Boolean) {
        val sourceMode = _state.value.alarmSettings.sourceMode
        runCatching { alarmSource.loadAlarms() }
            .onSuccess { loaded ->
                _state.update { current ->
                    if (current.alarmSettings.sourceMode != sourceMode) return@update current
                    val taskId = current.activeTaskId
                    val scoped = loaded.filter { alarm ->
                        taskId != null && (alarm.taskId == taskId || alarm.taskId == null)
                    }
                    val history = loaded.filter { it.taskId != null }
                        .groupBy { it.taskId.orEmpty() }
                    current.copy(
                        alarms = scoped,
                        alarmMatches = alarmMatchesFor(current.copy(alarms = scoped), scoped),
                        alarmHistory = current.alarmHistory + history,
                        status = if (showStatus) "已刷新${sourceMode.label}告警，共 ${scoped.size} 条。" else current.status,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                if (showStatus) _state.update { it.copy(error = "读取${sourceMode.label}失败：${readableError(error)}") }
            }
    }

    private fun seedDemoAlarmsForCurrentTask() {
        val snapshot = _state.value
        if (snapshot.alarmSettings.sourceMode != AlarmSourceMode.MOCK) {
            _state.update { it.copy(status = "演示模式需要使用模拟告警源；当前网管接口仍为二期预留。", error = null) }
            return
        }
        if (snapshot.racks.isEmpty() || snapshot.activeTaskId.isNullOrBlank()) {
            _state.update {
                it.copy(
                    status = "演示模式已开启；完成本次识别后将从真实设备动态生成告警。",
                    error = null,
                    alarms = emptyList(),
                    alarmMatches = emptyList(),
                )
            }
            return
        }
        val mock = alarmSource as? MockAlarmSource
        if (mock == null) {
            _state.update { it.copy(error = "当前告警数据源不是模拟源。") }
            return
        }
        val racks = snapshot.racks
        val taskId = snapshot.activeTaskId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { mock.seedDemoAlarms(racks = racks, taskId = taskId) }
                .onSuccess { generated ->
                    _state.update { current ->
                        if (current.activeTaskId != taskId) return@update current
                        val matches = alarmMatchesFor(current.copy(alarms = generated.alarms), generated.alarms)
                        current.copy(
                            alarms = generated.alarms,
                            alarmMatches = matches,
                            alarmHistory = current.alarmHistory + (taskId to generated.alarms),
                            alarmEffect = current.newAlarmEffect(generated.alarms.firstOrNull()),
                            status = buildString {
                                append("已根据本次识别的真实设备生成 ${generated.alarms.size} 条模拟告警。")
                                if (generated.notices.isNotEmpty()) append(" ${generated.notices.joinToString(" ")}")
                            },
                            error = null,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(error = "生成模拟告警失败：${readableError(error)}") } }
        }
    }

    private fun alarmMatchesFor(
        state: RackExcelUiState,
        alarms: List<AlarmRecord> = state.alarms,
    ): List<AlarmMatchResult> = if (state.racks.isEmpty()) {
        emptyList()
    } else {
        AlarmMatchPolicy.matchAll(alarms, state.racks, state.alarmSettings.matchConfig)
    }

    private fun refreshAlarmMatches() {
        _state.update { current ->
            current.copy(alarmMatches = alarmMatchesFor(current, current.alarms))
        }
    }

    private fun RackExcelUiState.newAlarmEffect(alarm: AlarmRecord?): AlarmUiEffect? {
        val record = alarm ?: return null
        if (record.status == AlarmStatus.HANDLED) return null
        return AlarmUiEffect(
            sequence = alarmEffectSequence.incrementAndGet(),
            title = "检测到新的${record.severity.label}告警",
            detail = record.description,
            playSound = alarmSettings.soundEnabled,
            vibrate = alarmSettings.vibrationEnabled,
        )
    }

    private fun persistAlarmHistory() {
        val snapshot = _state.value
        val taskId = snapshot.activeTaskId ?: return
        val records = snapshot.alarms.filter { it.taskId == taskId }
        if (records.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.load().firstOrNull { it.taskId == taskId }?.let { item ->
                historyStore.add(item.copy(alarmRecords = records))
            }
            _state.update {
                it.copy(
                    alarmHistory = it.alarmHistory + (taskId to records),
                    taskHistory = historyStore.load(),
                )
            }
        }
    }

    private fun initialState(): RackExcelUiState {
        val profileCollection = configStore.loadProfiles()
        val config = profileCollection.active.toConfig()
        val settings = settingsStore.load()
        val alarmSettings = alarmSettingsStore.load()
        val networkGateway = networkGatewayConfigStore.load()
        val active = promptStore.active()
        val base = RackExcelUiState(
            modelProfiles = profileCollection.profiles,
            activeModelProfileId = profileCollection.activeId,
            modelProfileEditorId = profileCollection.activeId,
            modelProfileName = profileCollection.active.name,
            url = config.url,
            model = config.model,
            apiKey = config.apiKey,
            concurrency = config.concurrency.coerceIn(1, TaskPolicy.MAX_CONCURRENCY),
            filePrefix = settings.filePrefix,
            autoSave = settings.autoSave,
            retainOriginalImages = settings.retainOriginalImages,
            promptTemplates = promptStore.templates(),
            activePromptId = active.id,
            promptEditorId = active.id,
            promptEditorName = active.name,
            promptEditorVersion = active.version,
            promptEditorContent = active.content,
            taskHistory = historyStore.load(),
            connectedComputer = connectedComputerStore.connectedComputer(),
            alarmSettings = alarmSettings,
            networkGateway = networkGateway,
        )
        return taskSessionStore.load()?.let { session -> base.restoreActiveTaskSession(session) } ?: base
    }

    private fun RackExcelUiState.toActiveTaskSession(): ActiveTaskSession {
        val startedAt = taskStartedAtMillis ?: System.currentTimeMillis()
        val taskId = activeTaskId ?: "TASK-$startedAt"
        val reviews = imageOutcomes.map { outcome ->
            TaskReviewSnapshot(
                imageId = outcome.imageId,
                imageName = outcome.imageName,
                cabinetId = outcome.cabinetId,
                reviewImagePath = outcome.reviewImagePath ?: images.firstOrNull { it.id == outcome.imageId }?.archivedPath,
                state = outcome.state.label,
                reviewConfirmed = outcome.reviewConfirmed,
                message = outcome.message,
                retryCount = outcome.retryCount,
                repairEvents = outcome.repairEvents.map { event -> "第 ${event.retry} 次：${event.message}" },
                uncertain = outcome.rack?.uncertain.orEmpty(),
                riskTexts = outcome.rack?.riskCandidates.orEmpty().map { risk -> "${risk.category}：${risk.description}" },
                rack = outcome.rack,
            )
        }
        val history = TaskHistoryItem(
            taskId = taskId,
            roomName = roomName,
            createdAtMillis = startedAt,
            startedAtMillis = startedAt,
            finishedAtMillis = taskFinishedAtMillis ?: startedAt,
            cabinetIds = racks.map(Rack::cabinetId),
            summary = ExportSummary.from(imageOutcomes),
            resultName = resultName,
            resultUri = resultUri?.toString(),
            efficiency = efficiency,
            confirmationMode = confirmationMode,
            recognitionFinishedAtMillis = recognitionFinishedAtMillis,
            reviewStartedAtMillis = reviewStartedAtMillis,
            reviewItems = reviews,
            desktopDelivery = taskHistory.firstOrNull { it.taskId == taskId }?.desktopDelivery,
            alarmRecords = alarms.filter { it.taskId == taskId },
        )
        return ActiveTaskSession(
            history = history,
            images = images.map { image ->
                TaskSessionImage(
                    id = image.id,
                    name = image.name,
                    uri = image.uri.toString(),
                    archivedPath = image.archivedPath,
                    originalPath = image.originalPath,
                )
            },
            status = status,
        )
    }

    private fun RackExcelUiState.restoreActiveTaskSession(session: ActiveTaskSession): RackExcelUiState {
        val sessionHistory = TaskSessionRecoveryPolicy.preferDurableDelivery(session.history, taskHistory)
        val restoredImages = session.images.map { image ->
            val reviewArchive = image.archivedPath?.let(::File)?.takeIf { it.isFile }
            val originalArchive = image.originalPath?.let(::File)?.takeIf { it.isFile }
            SelectedImage(
                uri = (originalArchive ?: reviewArchive)?.let(Uri::fromFile) ?: Uri.parse(image.uri),
                name = image.name,
                id = image.id,
                archivedPath = reviewArchive?.absolutePath ?: image.archivedPath,
                originalPath = originalArchive?.absolutePath ?: image.originalPath,
            )
        }
        val outcomes = sessionHistory.reviewItems.map { review ->
            val restoredState = ImageProcessingState.entries.firstOrNull { it.label == review.state }
                ?.takeUnless { it == ImageProcessingState.ANALYZING || it == ImageProcessingState.REPAIRING }
                ?: if (review.rack != null) ImageProcessingState.COMPLETED else ImageProcessingState.QUEUED
            ImageOutcome(
                imageName = review.imageName,
                rack = review.rack,
                state = restoredState,
                retryCount = review.retryCount,
                message = review.message,
                repairEvents = review.repairEvents.mapIndexed { index, value ->
                    RepairEvent(index + 1, TaskHistoryText.normalizeRepairMessage(value))
                },
                imageId = review.imageId,
                reviewConfirmed = review.reviewConfirmed,
                reviewImagePath = review.reviewImagePath,
            )
        }
        val racks = RackOrdering.sort(outcomes.mapNotNull(ImageOutcome::rack)).racks
        val restoredStatus = when {
            sessionHistory.finishedAtMillis > sessionHistory.startedAtMillis && sessionHistory.resultName != null ->
                "已恢复本次交付，可继续查看、分享或发送到电脑。"
            outcomes.isNotEmpty() -> "已恢复上次现场任务，可继续复核或重新识别。"
            restoredImages.isNotEmpty() -> "已恢复图片队列，可继续采集或开始识别。"
            else -> session.status
        }
        val sessionIsCompleted = sessionHistory.resultName != null ||
            sessionHistory.finishedAtMillis > sessionHistory.startedAtMillis
        return copy(
            roomName = sessionHistory.roomName,
            taskHistory = if (sessionIsCompleted) {
                (taskHistory + listOf(sessionHistory))
                    .distinctBy(TaskHistoryItem::taskId)
                    .sortedByDescending(TaskHistoryItem::createdAtMillis)
            } else {
                taskHistory
            },
            images = restoredImages,
            imageOutcomes = outcomes,
            activeTaskId = sessionHistory.taskId,
            taskStartedAtMillis = sessionHistory.startedAtMillis,
            taskFinishedAtMillis = sessionHistory.finishedAtMillis.takeIf { it > sessionHistory.startedAtMillis },
            recognitionFinishedAtMillis = sessionHistory.recognitionFinishedAtMillis,
            reviewStartedAtMillis = sessionHistory.reviewStartedAtMillis,
            isRunning = false,
            progress = if (outcomes.isEmpty()) 0f else outcomes.count { it.state != ImageProcessingState.QUEUED }.toFloat() / outcomes.size,
            status = restoredStatus,
            racks = racks,
            resultUri = sessionHistory.resultUri?.let(Uri::parse),
            resultName = sessionHistory.resultName,
            efficiency = sessionHistory.efficiency,
            confirmationMode = sessionHistory.confirmationMode,
            exportPhase = if (sessionHistory.resultName != null) ExportPhase.COMPLETED else ExportPhase.WAITING_CONFIRMATION,
            exportProgress = if (sessionHistory.resultName != null) 1f else 0f,
            alarms = sessionHistory.alarmRecords,
            alarmMatches = emptyList(),
            alarmHistory = sessionHistory.alarmRecords
                .takeIf { it.isNotEmpty() }
                ?.let { mapOf(sessionHistory.taskId to it) }
                .orEmpty(),
        )
    }

    private fun defaultPrompt() = PromptTemplate(
        id = "gov-rack-standard",
        name = "通信机房上架图通用版",
        version = "v2.5",
        content = RackPrompt.DEFAULT,
        updatedAtMillis = 0L,
        isBuiltIn = true,
    )

    private fun applyModelProfileCollection(collection: ModelProfileCollection, status: String) {
        val active = collection.active
        _state.update {
            it.copy(
                modelProfiles = collection.profiles,
                activeModelProfileId = collection.activeId,
                modelProfileEditorId = active.id,
                modelProfileName = active.name,
                url = active.url,
                model = active.model,
                apiKey = active.apiKey,
                concurrency = active.concurrency.coerceIn(1, TaskPolicy.MAX_CONCURRENCY),
                status = status,
                error = null,
            )
        }
    }

    private fun RackExcelUiState.currentModelProfile(): ModelProfile =
        modelProfiles.firstOrNull { it.id == activeModelProfileId }
            ?: ModelProfile(
                id = activeModelProfileId.ifBlank { "platform-default" },
                name = modelProfileName.ifBlank { "平台默认配置" },
                url = url,
                model = model,
                apiKey = apiKey,
                concurrency = concurrency,
                isBuiltIn = modelProfiles.isEmpty(),
            )

    /** Removes a completed task from the active workspace after the user deletes its history. */
    private fun RackExcelUiState.clearCompletedWorkspace(
        status: String,
        taskHistory: List<TaskHistoryItem> = emptyList(),
    ): RackExcelUiState = copy(
        roomName = "",
        images = emptyList(),
        imageOutcomes = emptyList(),
        activeTaskId = null,
        taskStartedAtMillis = null,
        taskFinishedAtMillis = null,
        progress = 0f,
        racks = emptyList(),
        resultUri = null,
        resultName = null,
        desktopTransfer = DesktopTransferUiState(),
        alarms = emptyList(),
        alarmMatches = emptyList(),
        alarmEffect = null,
        taskHistory = taskHistory,
        status = status,
        error = null,
    )

    private fun RackExcelUiState.toProfileCollection(): ModelProfileCollection {
        val current = currentModelProfile().copy(
            name = modelProfileName,
            url = url,
            model = model,
            apiKey = apiKey,
            concurrency = concurrency,
        )
        val profiles = modelProfiles.map { if (it.id == current.id) current else it }
            .ifEmpty { listOf(current) }
        return ModelProfilePolicy.normalize(profiles, current.id, current)
    }

    private fun ModelProfileCollection.withActiveHealth(
        checkedAtMillis: Long,
        latencyMillis: Long?,
        succeeded: Boolean,
    ): ModelProfileCollection {
        val updated = profiles.map { profile ->
            if (profile.id == activeId) {
                profile.copy(
                    lastCheckedAtMillis = checkedAtMillis,
                    lastLatencyMillis = latencyMillis,
                    lastCheckSucceeded = succeeded,
                )
            } else {
                profile
            }
        }
        return ModelProfilePolicy.normalize(updated, activeId, active)
    }

    private fun RackExcelUiState.toConfig() = ModelConfig(
        url = url.trim(),
        model = model.trim(),
        apiKey = apiKey,
        concurrency = concurrency.coerceIn(1, TaskPolicy.MAX_CONCURRENCY),
    )

    private fun com.rackexcel.mobile.receiver.ReceiverConnection.toSafeComputer(): ConnectedComputer = ConnectedComputer(
        receiverId = receiverId,
        receiverName = receiverName,
        host = host,
        port = port,
        deviceId = deviceId,
        expiresAtEpochMillis = expiresAtEpochMillis,
        protocolVersion = protocolVersion,
        pairedAtEpochMillis = pairedAtEpochMillis,
    )

    private fun ReceiverTransferReceipt.toHistoryDelivery(
        receiverId: String,
        receiverName: String,
    ): DesktopDeliveryReceipt = DesktopDeliveryReceipt(
        receiverId = receiverId,
        receiverName = receiverName,
        uploadId = uploadId,
        fileName = fileName,
        savedPath = savedPath,
        sha256 = sha256,
        sizeBytes = size,
        receivedAtMillis = receivedAtEpochMillis,
    )

    private fun readableDesktopError(error: Throwable, endpoint: String? = null): String =
        DesktopReceiverDiagnostics.readableError(error, endpoint)

    private fun readableError(error: Throwable?): String =
        error?.message?.trim().takeUnless { it.isNullOrBlank() }
            ?: "请检查网络、图片质量和模型配置。"
}
