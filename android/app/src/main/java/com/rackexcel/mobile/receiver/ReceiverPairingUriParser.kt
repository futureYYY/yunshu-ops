package com.rackexcel.mobile.receiver

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ReceiverPairingUriParser {
    private const val SCHEME = "yunshu-receiver"
    private const val AUTHORITY = "pair"
    private const val PROTOCOL_VERSION = "1"
    private val requiredFields = setOf(
        "v",
        "host",
        "port",
        "receiver_id",
        "receiver_name",
        "pairing_secret",
        "expires",
    )

    fun parse(uriText: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L): ReceiverPairingInvite {
        val uri = runCatching { URI(uriText.trim()) }
            .getOrElse { throw ReceiverPairingUriException("桌面接收器二维码格式无效") }
        if (!uri.isAbsolute || !uri.scheme.equals(SCHEME, ignoreCase = true) || uri.host != AUTHORITY) {
            throw ReceiverPairingUriException("这不是云枢智维桌面接收器二维码")
        }
        if (!uri.userInfo.isNullOrBlank() || uri.port != -1 || !uri.path.isNullOrBlank() || !uri.fragment.isNullOrBlank()) {
            throw ReceiverPairingUriException("桌面接收器二维码格式无效")
        }

        val query = parseQuery(uri.rawQuery)
        if (query.keys != requiredFields) {
            throw ReceiverPairingUriException("桌面接收器二维码缺少必要参数")
        }
        if (query.getValue("v") != PROTOCOL_VERSION) {
            throw ReceiverPairingUriException("桌面接收器协议版本不匹配")
        }

        val host = query.getValue("host")
        if (!isPrivateIpv4(host)) {
            throw ReceiverPairingUriException("桌面接收器地址必须是受控局域网 IPv4 地址")
        }
        val port = query.getValue("port").toIntOrNull()?.takeIf { it in 1..65_535 }
            ?: throw ReceiverPairingUriException("桌面接收器端口无效")
        val expires = query.getValue("expires").toLongOrNull()?.takeIf { it > nowEpochSeconds }
            ?: throw ReceiverPairingUriException("桌面接收器二维码已过期，请在电脑端刷新后重试")
        val receiverId = query.getValue("receiver_id").trim().takeIf { it.isNotEmpty() }
            ?: throw ReceiverPairingUriException("桌面接收器标识无效")
        val receiverName = query.getValue("receiver_name").trim().takeIf { it.isNotEmpty() }
            ?: throw ReceiverPairingUriException("桌面接收器名称无效")
        val pairingSecret = query.getValue("pairing_secret").trim().takeIf { it.isNotEmpty() }
            ?: throw ReceiverPairingUriException("桌面接收器配对信息无效")

        return ReceiverPairingInvite(
            host = host,
            port = port,
            receiverId = receiverId,
            receiverName = receiverName,
            pairingSecret = pairingSecret,
            expiresAtEpochSeconds = expires,
        )
    }

    fun isPrivateIpv4(value: String): Boolean {
        val octets = value.split('.')
        if (octets.size != 4) return false
        val values = octets.map { token ->
            if (token.isBlank() || token.length > 3 || (token.length > 1 && token.startsWith('0'))) return false
            token.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        return when (values[0]) {
            10 -> true
            172 -> values[1] in 16..31
            192 -> values[1] == 168
            // Some enterprise WLANs use RFC 6598 shared address space locally.
            // Keep the accepted range narrow; public addresses stay rejected.
            100 -> values[1] in 64..127
            else -> false
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) throw ReceiverPairingUriException("桌面接收器二维码缺少必要参数")
        val values = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { component ->
            val separator = component.indexOf('=')
            if (separator <= 0) throw ReceiverPairingUriException("桌面接收器二维码格式无效")
            val key = decode(component.substring(0, separator))
            val value = decode(component.substring(separator + 1))
            if (key.isBlank() || values.put(key, value) != null) {
                throw ReceiverPairingUriException("桌面接收器二维码包含重复参数")
            }
        }
        return values
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrElse { throw ReceiverPairingUriException("桌面接收器二维码编码无效") }
}
