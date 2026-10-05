package com.rackexcel.mobile.receiver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReceiverPairingUriParserTest {
    @Test
    fun parsesAnUnexpiredPrivateNetworkInvite() {
        val invite = ReceiverPairingUriParser.parse(
            "yunshu-receiver://pair?v=1&host=192.0.2.28&port=48120" +
                "&receiver_id=receiver-001&receiver_name=DESKTOP-01" +
                "&pairing_secret=one-time-secret&expires=1800000000",
            nowEpochSeconds = 1_700_000_000,
        )

        assertEquals("192.0.2.28", invite.host)
        assertEquals(48120, invite.port)
        assertEquals("receiver-001", invite.receiverId)
        assertEquals("DESKTOP-01", invite.receiverName)
        assertEquals("http://192.0.2.28:48120", invite.baseUrl)
    }

    @Test
    fun rejectsAnExpiredInvite() {
        assertFailsWith<ReceiverPairingUriException> {
            ReceiverPairingUriParser.parse(
                validUri(host = "192.0.2.28", expires = 1_700_000_000),
                nowEpochSeconds = 1_700_000_000,
            )
        }
    }

    @Test
    fun rejectsLoopbackHost() {
        assertFailsWith<ReceiverPairingUriException> {
            ReceiverPairingUriParser.parse(validUri(host = "127.0.0.1"), nowEpochSeconds = 1_700_000_000)
        }
    }

    @Test
    fun rejectsPublicHost() {
        assertFailsWith<ReceiverPairingUriException> {
            ReceiverPairingUriParser.parse(validUri(host = "8.8.8.8"), nowEpochSeconds = 1_700_000_000)
        }
    }

    @Test
    fun acceptsEnterpriseSharedAddressSpaceHost() {
        val invite = ReceiverPairingUriParser.parse(
            validUri(host = "192.0.2.10"),
            nowEpochSeconds = 1_700_000_000,
        )
        assertEquals("192.0.2.10", invite.host)
    }

    @Test
    fun acceptsWindowsMobileHotspotHost() {
        val invite = ReceiverPairingUriParser.parse(
            validUri(host = "192.168.137.1"),
            nowEpochSeconds = 1_700_000_000,
        )
        assertEquals("192.168.137.1", invite.host)
    }

    @Test
    fun rejectsSharedAddressSpaceOutsideLocalRange() {
        assertFailsWith<ReceiverPairingUriException> {
            ReceiverPairingUriParser.parse(validUri(host = "192.0.2.12"), nowEpochSeconds = 1_700_000_000)
        }
    }

    @Test
    fun rejectsMulticastHost() {
        assertFailsWith<ReceiverPairingUriException> {
            ReceiverPairingUriParser.parse(validUri(host = "239.1.2.3"), nowEpochSeconds = 1_700_000_000)
        }
    }

    @Test
    fun rejectsDuplicateProtocolValues() {
        assertFailsWith<ReceiverPairingUriException> {
            ReceiverPairingUriParser.parse(
                validUri(host = "192.0.2.28").replace("v=1", "v=1&v=1"),
                nowEpochSeconds = 1_700_000_000,
            )
        }
    }

    private fun validUri(host: String, expires: Long = 1_800_000_000): String =
        "yunshu-receiver://pair?v=1&host=$host&port=48120" +
            "&receiver_id=receiver-001&receiver_name=DESKTOP-01" +
            "&pairing_secret=one-time-secret&expires=$expires"
}
