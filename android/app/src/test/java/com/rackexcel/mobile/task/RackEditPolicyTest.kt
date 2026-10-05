package com.rackexcel.mobile.task

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RackEditPolicyTest {
    private val rack = Rack(
        cabinetId = "K03",
        imageName = "K03.jpg",
        devices = listOf(
            Device(type = "交换机", bottomU = 43, heightU = 1, evidence = "右侧轨 U43"),
            Device(type = "服务器", bottomU = 21, heightU = 2, evidence = "右侧轨 U21-U22"),
        ),
        uncertain = listOf("U21 边界被线缆遮挡"),
    )

    @Test
    fun edit_updates_a_single_device_and_preserves_the_other_device() {
        val result = RackEditPolicy.updateDevice(
            rack = rack,
            deviceIndex = 1,
            type = "服务器",
            bottomU = 20,
            heightU = 2,
        )

        assertEquals(2, result.devices.size)
        assertEquals(20, result.devices.single { it.evidence == "右侧轨 U21-U22" }.bottomU)
        assertEquals(43, result.devices.single { it.type == "交换机" }.bottomU)
    }

    @Test
    fun edit_rejects_u_overlap_with_another_device() {
        assertFailsWith<IllegalArgumentException> {
            RackEditPolicy.updateDevice(
                rack = rack,
                deviceIndex = 1,
                type = "服务器",
                bottomU = 42,
                heightU = 2,
            )
        }
    }

    @Test
    fun edit_accepts_extended_device_types() {
        val result = RackEditPolicy.updateDevice(
            rack = rack,
            deviceIndex = 0,
            type = "路由器",
            bottomU = 43,
            heightU = 1,
        )

        assertEquals("路由器", result.devices.single { it.bottomU == 43 }.type)
    }
}
