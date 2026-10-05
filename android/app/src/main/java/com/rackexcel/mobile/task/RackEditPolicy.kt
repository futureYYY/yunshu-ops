package com.rackexcel.mobile.task

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.DeviceTypePolicy
import com.rackexcel.mobile.model.Rack

/** Validates edits made in the field-review page before they reach Excel output. */
object RackEditPolicy {
    val allowedTypes = DeviceTypePolicy.allowedTypes

    fun updateDevice(
        rack: Rack,
        deviceIndex: Int,
        type: String,
        bottomU: Int,
        heightU: Int,
    ): Rack {
        require(deviceIndex in rack.devices.indices) { "未找到需要调整的设备。" }
        require(type in allowedTypes) { "设备类型请选择交换机、服务器、传输设备、路由器或自定义设备。" }
        require(bottomU in 1..47) { "设备下沿 U 位需在 1 至 47 之间。" }
        require(heightU in 1..2) { "设备高度仅支持 1U 或 2U。" }
        require(bottomU + heightU - 1 <= 47) { "设备占用 U 位超出机柜顶部。" }

        val changed = rack.devices[deviceIndex].copy(
            type = type,
            bottomU = bottomU,
            heightU = heightU,
        )
        val devices = rack.devices.mapIndexed { index, device -> if (index == deviceIndex) changed else device }
        val occupied = mutableSetOf<Int>()
        devices.forEach { device ->
            (device.bottomU until device.bottomU + device.heightU).forEach { u ->
                require(occupied.add(u)) { "调整后与其他设备在 U$u 重叠。" }
            }
        }
        return rack.copy(devices = devices.sortedWith(compareByDescending<Device> { it.bottomU }.thenBy { it.type }))
    }
}
