package com.rackexcel.mobile.receiver

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopReceiverClientTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun pairingChecksHealthAndSendsTheOneTimeSecretOnlyToThePairEndpoint() = runBlocking {
        server.start()
        server.enqueue(jsonResponse("""{"receiver_id":"receiver-001","receiver_name":"DESKTOP-01","protocol_version":"1","status":"ready"}"""))
        server.enqueue(jsonResponse("""{"receiverId":"receiver-001","receiverName":"DESKTOP-01","deviceId":"android-001","accessToken":"token-from-pairing","expiresAtUtc":"2027-08-17T00:00:00Z","protocolVersion":"1"}"""))
        val client = clientForServer()

        val connection = client.pair(invite(), deviceId = "android-001", deviceName = "现场手机")

        assertEquals("receiver-001", connection.receiverId)
        assertEquals("DESKTOP-01", connection.receiverName)
        assertFalse(connection.toString().contains("token-from-pairing"))
        val healthRequest = assertNotNull(server.takeRequest())
        assertEquals("/api/v1/health", healthRequest.path)
        val pairRequest = assertNotNull(server.takeRequest())
        assertEquals("/api/v1/pair", pairRequest.path)
        val body = JSONObject(pairRequest.body.readUtf8())
        assertEquals("one-time-secret", body.getString("pairingSecret"))
        assertEquals("android-001", body.getString("deviceId"))
    }

    @Test
    fun pairingAcceptsTheDesktopEpochExpiryCompatibilityField() = runBlocking {
        server.start()
        server.enqueue(jsonResponse("""{"receiver_id":"receiver-001","receiver_name":"DESKTOP-01","protocol_version":"1","status":"ready"}"""))
        server.enqueue(
            jsonResponse(
                "\uFEFF" + """{"receiverId":"receiver-001","receiverName":"DESKTOP-01","deviceId":"android-001","accessToken":"token-from-pairing","expiresAtEpochMillis":1800000000000,"protocolVersion":"1"}""",
            ),
        )

        val connection = clientForServer().pair(invite(), deviceId = "android-001", deviceName = "现场手机")

        assertEquals(1_800_000_000_000L, connection.expiresAtEpochMillis)
        assertEquals("token-from-pairing", connection.accessToken)
    }

    @Test
    fun healthRetriesTransientServerFailureWithTheProtocolSchedule() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"error\":{\"message\":\"busy\"}}"))
        server.enqueue(jsonResponse("""{"receiver_id":"receiver-001","receiver_name":"DESKTOP-01","protocol_version":"1","status":"ready"}"""))
        val retries = mutableListOf<Int>()

        val health = clientForServer().health(invite()) { retry, _ -> retries += retry }

        assertEquals("ready", health.status)
        assertEquals(listOf(1), retries)
        assertEquals("/api/v1/health", assertNotNull(server.takeRequest()).path)
        assertEquals("/api/v1/health", assertNotNull(server.takeRequest()).path)
    }

    @Test
    fun calculatesTheWholeExcelDigestFromAStream() = runBlocking {
        val sourceBytes = "abc".encodeToByteArray()

        val digest = clientForServer().calculateSha256(
            ReceiverUploadSource(
                fileName = "result.xlsx",
                totalBytes = sourceBytes.size.toLong(),
                openStream = { ByteArrayInputStream(sourceBytes) },
            ),
        )

        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            digest,
        )
    }

    @Test
    fun uploadSkipsConfirmedChunksAndSendsChunkDigestAndBearerToken() = runBlocking {
        server.start()
        server.enqueue(
            jsonResponse(
                """{"uploadId":"upload-001","chunkSize":4,"receivedChunks":[0],"totalBytes":7,"fileName":"result.xlsx"}""",
            ),
        )
        server.enqueue(jsonResponse("""{"upload_id":"upload-001","chunk_index":1,"received":true}"""))
        server.enqueue(
            jsonResponse(
                """{"uploadId":"upload-001","fileName":"result (1).xlsx","savedPath":"D:\\\\deliveries\\\\result (1).xlsx","size":7,"sha256":"AABB","receivedAtUtc":"2026-08-17T08:30:00Z","deviceName":"现场手机"}""",
            ),
        )
        val sourceBytes = "1234567".encodeToByteArray()
        val checkpoints = mutableListOf<ReceiverUploadCheckpoint>()

        val receipt = clientForServer().upload(
            connection = connection(),
            source = ReceiverUploadSource(
                fileName = "result.xlsx",
                totalBytes = sourceBytes.size.toLong(),
                openStream = { ByteArrayInputStream(sourceBytes) },
            ),
            taskId = "task-001",
            onCheckpoint = { checkpoints += it },
        )

        assertEquals("result (1).xlsx", receipt.fileName)
        assertEquals(7L, receipt.size)
        val initRequest = assertNotNull(server.takeRequest())
        assertEquals("/api/v1/uploads/init", initRequest.path)
        assertEquals(64, JSONObject(initRequest.body.readUtf8()).getString("sha256").length)
        val chunkRequest = assertNotNull(server.takeRequest())
        assertEquals("/api/v1/uploads/upload-001/chunks/1", chunkRequest.path)
        assertEquals("Bearer desktop-token", chunkRequest.getHeader("Authorization"))
        assertEquals(64, chunkRequest.getHeader("X-Chunk-Sha256")?.length)
        assertEquals("567", chunkRequest.body.readUtf8())
        val completeRequest = assertNotNull(server.takeRequest())
        assertEquals("/api/v1/uploads/upload-001/complete", completeRequest.path)
        assertTrue(checkpoints.last().receivedChunks.containsAll(setOf(0, 1)))
    }

    @Test
    fun uploadStartsANewSessionWhenTheDesktopHasClearedTheSavedCheckpoint() = runBlocking {
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"upload_not_found","message":"staging cleared"}}"""),
        )
        server.enqueue(
            jsonResponse(
                """{"uploadId":"upload-new","chunkSize":4,"receivedChunks":[],"totalBytes":4,"fileName":"result.xlsx"}""",
            ),
        )
        server.enqueue(jsonResponse("""{"upload_id":"upload-new","chunk_index":0,"received":true}"""))
        server.enqueue(
            jsonResponse(
                """{"uploadId":"upload-new","fileName":"result.xlsx","savedPath":"D:\\\\deliveries\\\\result.xlsx","size":4,"sha256":"AABB","receivedAtUtc":"2026-08-17T08:30:00Z","deviceName":"现场手机"}""",
            ),
        )

        val sourceBytes = "1234".encodeToByteArray()
        val receipt = clientForServer().upload(
            connection = connection(),
            source = ReceiverUploadSource("result.xlsx", sourceBytes.size.toLong()) { ByteArrayInputStream(sourceBytes) },
            taskId = "task-001",
            checkpoint = ReceiverUploadCheckpoint(
                taskId = "task-001",
                uploadId = "upload-stale",
                fileName = "result.xlsx",
                totalBytes = sourceBytes.size.toLong(),
                sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(sourceBytes)
                    .joinToString("") { "%02X".format(it.toInt() and 0xFF) },
                receivedChunks = emptySet(),
                receiverId = "receiver-001",
                updatedAtEpochMillis = 1_700_000_000_000L,
            ),
        )

        assertEquals("upload-new", receipt.uploadId)
        val staleInit = assertNotNull(server.takeRequest())
        assertEquals("upload-stale", JSONObject(staleInit.body.readUtf8()).optString("uploadId"))
        val freshInit = assertNotNull(server.takeRequest())
        assertFalse(JSONObject(freshInit.body.readUtf8()).has("uploadId"))
        assertEquals("/api/v1/uploads/upload-new/chunks/0", assertNotNull(server.takeRequest()).path)
        assertEquals("/api/v1/uploads/upload-new/complete", assertNotNull(server.takeRequest()).path)
    }

    private fun clientForServer(): DesktopReceiverClient = DesktopReceiverClient(
        httpClient = OkHttpClient(),
        endpointResolver = { _, _ -> server.url("/").toString().trimEnd('/') },
    )

    private fun invite(): ReceiverPairingInvite = ReceiverPairingInvite(
        host = "192.0.2.28",
        port = 48120,
        receiverId = "receiver-001",
        receiverName = "DESKTOP-01",
        pairingSecret = "one-time-secret",
        expiresAtEpochSeconds = 1_800_000_000,
    )

    private fun connection(): ReceiverConnection = ReceiverConnection(
        receiverId = "receiver-001",
        receiverName = "DESKTOP-01",
        host = "192.0.2.28",
        port = 48120,
        deviceId = "android-001",
        accessToken = "desktop-token",
        expiresAtEpochMillis = 1_800_000_000_000,
        protocolVersion = "1",
        pairedAtEpochMillis = 1_700_000_000_000,
    )

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
