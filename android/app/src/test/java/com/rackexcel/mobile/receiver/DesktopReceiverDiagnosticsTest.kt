package com.rackexcel.mobile.receiver

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DesktopReceiverDiagnosticsTest {
    @Test
    fun networkFailureNamesTheAttemptedEndpointAndDirectHotspotRecovery() {
        val message = DesktopReceiverDiagnostics.readableError(
            DesktopReceiverException.network(IllegalStateException("timeout")),
            endpoint = "192.0.2.10:58520",
        )

        assertContains(message, "192.0.2.10:58520")
        assertContains(message, "Windows 移动热点")
        assertContains(message, "刷新二维码")
    }

    @Test
    fun validationFailureKeepsTheReceiverMessage() {
        val message = DesktopReceiverDiagnostics.readableError(
            DesktopReceiverException.validation("桌面接收器二维码已过期，请在电脑端刷新后重试"),
        )

        assertEquals("桌面接收器二维码已过期，请在电脑端刷新后重试", message)
    }
}
