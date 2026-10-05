package com.rackexcel.mobile.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, valid JPEG data URI used solely to validate a provider's image input path.
 * Keeping it here lets the request shape and payload integrity be unit tested.
 */
object ConnectionProbePayload {
    const val imageDataUri: String =
        "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBAUEBAYFBQUGBgYHCQ4JCQgICRINDQoOFRIWFhUSFBQXGiEcFxgfGRQUHScdHyIjJSUlFhwpLCgkKyEkJST/2wBDAQYGBgkICREJCREkGBQYJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCT/wAARCAAgACADASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAb/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdAL9HAAAAAAP/2Q=="

    fun body(model: String): String {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "请仅返回严格 JSON：{\"ready\":true}"))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", imageDataUri).put("detail", "low")),
            )
        return JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "你是连接自检助手。只返回 JSON。"))
                    .put(JSONObject().put("role", "user").put("content", content)),
            )
            .toString()
    }
}
