package com.rackexcel.mobile.alarm

import android.content.Context
import com.rackexcel.mobile.model.Rack

/** Source contract consumed by a future repository/ViewModel; it has no Compose/UI dependency. */
interface AlarmSource {
    val mode: AlarmSourceMode

    suspend fun loadAlarms(): List<AlarmRecord>
    suspend fun injectAlarm(alarm: AlarmRecord): AlarmRecord
    suspend fun updateAlarm(alarm: AlarmRecord): AlarmRecord
}

/**
 * Offline source for the competition demo. It reads and writes the local JSON
 * record store; the only demo-specific behavior is [seedDemoAlarms], which
 * derives records from the caller's current recognized racks.
 */
class MockAlarmSource(
    private val store: AlarmRecordStore,
) : AlarmSource {
    override val mode: AlarmSourceMode = AlarmSourceMode.MOCK
    private val lock = Any()

    override suspend fun loadAlarms(): List<AlarmRecord> = synchronized(lock) {
        store.loadAlarms().sortedByDescending(AlarmRecord::occurredAtMillis)
    }

    override suspend fun injectAlarm(alarm: AlarmRecord): AlarmRecord = synchronized(lock) {
        val current = store.loadAlarms()
        require(current.none { it.alarmId == alarm.alarmId }) { "告警 ID 已存在：${alarm.alarmId}" }
        store.saveAlarms(current + alarm)
        alarm
    }

    override suspend fun updateAlarm(alarm: AlarmRecord): AlarmRecord = synchronized(lock) {
        val current = store.loadAlarms()
        require(current.any { it.alarmId == alarm.alarmId }) { "未找到需要更新的告警：${alarm.alarmId}" }
        store.saveAlarms(current.map { currentAlarm -> if (currentAlarm.alarmId == alarm.alarmId) alarm else currentAlarm })
        alarm
    }

    /** Replaces mock records with alerts derived from the current task's actual rack/device data. */
    suspend fun seedDemoAlarms(
        racks: List<Rack>,
        taskId: String? = null,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ): MockAlarmGenerationResult = synchronized(lock) {
        MockAlarmGenerator.generate(racks, taskId, occurredAtMillis).also { generation ->
            store.saveAlarms(generation.alarms)
        }
    }

    /** Replaces the editable local JSON content, used by the demo-template importer. */
    suspend fun importJson(payload: String): List<AlarmRecord> = synchronized(lock) {
        val alarms = AlarmJsonCodec.decode(payload)
        store.saveAlarms(alarms)
        alarms
    }
}

/** Gateway to be implemented when the real network-manager API becomes available. */
interface NetworkManagerAlarmGateway {
    suspend fun loadAlarms(): List<AlarmRecord>
    suspend fun updateAlarm(alarm: AlarmRecord): AlarmRecord
}

/**
 * Network-manager source placeholder. UI can switch to it safely; it gives a
 * clear unavailable state until a real [NetworkManagerAlarmGateway] is supplied.
 */
class NetworkManagerAlarmSource(
    private val gateway: NetworkManagerAlarmGateway = UnconfiguredNetworkManagerGateway,
) : AlarmSource {
    override val mode: AlarmSourceMode = AlarmSourceMode.NETWORK_MANAGER

    override suspend fun loadAlarms(): List<AlarmRecord> = gateway.loadAlarms()
    override suspend fun updateAlarm(alarm: AlarmRecord): AlarmRecord = gateway.updateAlarm(alarm)
    override suspend fun injectAlarm(alarm: AlarmRecord): AlarmRecord =
        throw AlarmSourceException("真实网管告警接口暂未接入，演示告警请切换至模拟告警源。")
}

private object UnconfiguredNetworkManagerGateway : NetworkManagerAlarmGateway {
    override suspend fun loadAlarms(): List<AlarmRecord> =
        throw AlarmSourceException("真实网管告警接口暂未配置，请切换至模拟告警源。")

    override suspend fun updateAlarm(alarm: AlarmRecord): AlarmRecord =
        throw AlarmSourceException("真实网管告警接口暂未配置，无法回写告警状态。")
}

/** Android factory used by settings/configuration code to switch sources without a UI branch. */
class AlarmSourceFactory(
    context: Context,
    private val networkGatewayProvider: (NetworkGatewayConfig) -> NetworkManagerAlarmGateway = ::ConfiguredNetworkManagerGateway,
) {
    private val mockStore = AlarmMockJsonStore(context)

    fun create(mode: AlarmSourceMode, gatewayConfig: NetworkGatewayConfig = NetworkGatewayConfig()): AlarmSource = when (mode) {
        AlarmSourceMode.MOCK -> MockAlarmSource(mockStore)
        AlarmSourceMode.NETWORK_MANAGER -> NetworkManagerAlarmSource(networkGatewayProvider(gatewayConfig))
    }
}
