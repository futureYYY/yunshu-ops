package com.rackexcel.mobile.network

import org.json.JSONArray
import org.json.JSONObject

object VisionRequestBuilder {
    fun build(
        model: String,
        systemPrompt: String,
        originalImageDataUri: String,
        cropDataUris: List<String>,
    ): String {
        val userContent = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "请审阅这张完整机柜原图。下面的细节图均来自同一张原图，仅用于放大读取，不代表新的机柜。"))
            .put(imagePart(originalImageDataUri))

        val labels = listOf("顶部", "上部", "中部", "底部")
        cropDataUris.take(4).forEachIndexed { index, dataUri ->
            userContent
                .put(JSONObject().put("type", "text").put("text", "同一张完整机柜原图的${labels.getOrElse(index) { "局部" }}细节"))
                .put(imagePart(dataUri))
        }

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userContent))

        return JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("messages", messages)
            .toString()
    }

    private fun imagePart(dataUri: String): JSONObject = JSONObject()
        .put("type", "image_url")
        .put("image_url", JSONObject().put("url", dataUri).put("detail", "high"))
}
