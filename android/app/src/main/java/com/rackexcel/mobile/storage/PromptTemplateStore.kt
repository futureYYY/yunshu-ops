package com.rackexcel.mobile.storage

import android.content.Context
import com.rackexcel.mobile.RackPrompt
import org.json.JSONArray
import org.json.JSONObject

data class PromptTemplate(
    val id: String,
    val name: String,
    val version: String,
    val content: String,
    val updatedAtMillis: Long,
    val isBuiltIn: Boolean,
)

class PromptTemplateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun templates(): List<PromptTemplate> {
        val stored = preferences.getString(KEY_TEMPLATES, null)?.let(::parseTemplates).orEmpty()
        val merged = listOf(defaultTemplate()) + stored.filter { it.id != DEFAULT_ID }
        return merged.sortedWith(compareByDescending<PromptTemplate> { it.id == activeId() }.thenBy { it.name })
    }

    fun active(): PromptTemplate {
        val templates = templates()
        return templates.firstOrNull { it.id == activeId() } ?: defaultTemplate()
    }

    fun save(template: PromptTemplate) {
        require(template.name.isNotBlank()) { "提示词名称不能为空" }
        require(template.content.isNotBlank()) { "提示词内容不能为空" }
        val updated = templates().filterNot { it.id == template.id || it.id == DEFAULT_ID } + template.copy(isBuiltIn = false)
        preferences.edit().putString(KEY_TEMPLATES, serialize(updated)).apply()
    }

    fun setActive(id: String) {
        if (templates().any { it.id == id }) preferences.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun delete(id: String) {
        if (id == DEFAULT_ID) return
        val updated = templates().filterNot { it.id == id || it.id == DEFAULT_ID }
        val editor = preferences.edit().putString(KEY_TEMPLATES, serialize(updated))
        if (activeId() == id) editor.putString(KEY_ACTIVE, DEFAULT_ID)
        editor.apply()
    }

    private fun activeId(): String = preferences.getString(KEY_ACTIVE, DEFAULT_ID) ?: DEFAULT_ID

    private fun defaultTemplate() = PromptTemplate(
        id = DEFAULT_ID,
        name = "通信机房上架图通用版",
        version = "v2.5",
        content = RackPrompt.DEFAULT,
        updatedAtMillis = 0L,
        isBuiltIn = true,
    )

    private fun serialize(items: List<PromptTemplate>): String = JSONArray().apply {
        items.filterNot { it.id == DEFAULT_ID }.forEach { item ->
            put(JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("version", item.version)
                .put("content", item.content)
                .put("updatedAtMillis", item.updatedAtMillis))
        }
    }.toString()

    private fun parseTemplates(raw: String): List<PromptTemplate> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val content = item.optString("content")
                if (id.isBlank() || name.isBlank() || content.isBlank()) continue
                add(PromptTemplate(id, name, item.optString("version", "自定义"), content, item.optLong("updatedAtMillis"), false))
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFERENCES = "yunshu_prompt_templates"
        const val KEY_TEMPLATES = "templates"
        const val KEY_ACTIVE = "active"
        const val DEFAULT_ID = "gov-rack-standard"
    }
}
