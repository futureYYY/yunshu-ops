package com.rackexcel.mobile.model

/** Shared registration vocabulary used by model parsing, field review and Excel output. */
object DeviceTypePolicy {
    const val SWITCH = "交换机"
    const val SERVER = "服务器"
    const val TRANSMISSION = "传输设备"
    const val ROUTER = "路由器"
    const val CUSTOM = "自定义设备"

    val standardTypes: List<String> = listOf(SWITCH, SERVER, TRANSMISSION, ROUTER)
    val allowedTypes: Set<String> = (standardTypes + CUSTOM).toSet()

    fun isAllowed(type: String): Boolean = type.trim() in allowedTypes
}
