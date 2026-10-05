package com.rackexcel.mobile.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the active field task separately from the completed-task history.
 * It contains no model credentials or access tokens; image sources are paired
 * with an app-private review copy when one exists.
 */
data class TaskSessionImage(
    val id: String,
    val name: String,
    val uri: String,
    val archivedPath: String? = null,
    val originalPath: String? = null,
)

data class ActiveTaskSession(
    val history: TaskHistoryItem,
    val images: List<TaskSessionImage>,
    val status: String = "",
)

class TaskSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): ActiveTaskSession? = runCatching {
        val root = JSONObject(preferences.getString(KEY_SESSION, "") ?: "")
        val history = TaskHistoryJson.decode(root.optString(KEY_HISTORY_JSON)).firstOrNull()
            ?: return@runCatching null
        val images = root.optJSONArray(KEY_IMAGES)?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val uri = item.optString("uri").trim()
                    if (id.isNotBlank() && uri.isNotBlank()) {
                        add(
                            TaskSessionImage(
                                id = id,
                                name = item.optString("name").ifBlank { "机柜原图" },
                                uri = uri,
                                archivedPath = item.optString("archivedPath").trim().takeIf { it.isNotBlank() },
                                originalPath = item.optString("originalPath").trim().takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }
        }.orEmpty()
        ActiveTaskSession(history = history, images = images, status = root.optString(KEY_STATUS))
    }.getOrNull()

    fun save(session: ActiveTaskSession) {
        val root = JSONObject()
            .put(KEY_HISTORY_JSON, TaskHistoryJson.encode(listOf(session.history)))
            .put(KEY_STATUS, session.status)
            .put(KEY_IMAGES, JSONArray().apply {
                session.images.forEach { image ->
                    put(
                        JSONObject()
                            .put("id", image.id)
                            .put("name", image.name)
                            .put("uri", image.uri)
                            .put("archivedPath", image.archivedPath)
                            .put("originalPath", image.originalPath),
                    )
                }
            })
        preferences.edit().putString(KEY_SESSION, root.toString()).apply()
    }

    fun clear() = preferences.edit().remove(KEY_SESSION).apply()

    private companion object {
        const val PREFERENCES = "yunshu_task_session"
        const val KEY_SESSION = "active_session"
        const val KEY_HISTORY_JSON = "history_json"
        const val KEY_IMAGES = "images"
        const val KEY_STATUS = "status"
    }
}
