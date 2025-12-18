package com.droidrun.portal.appcard

import org.json.JSONArray
import org.json.JSONObject

/**
 * App Card 数据模型
 * 代表一个应用操作指南
 */
data class AppCard(
    val id: String,
    val packageName: String,
    val title: String,
    val keywords: List<String>,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("packageName", packageName)
            put("title", title)
            put("keywords", JSONArray(keywords))
            put("content", content)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AppCard {
            val keywordsArray = json.getJSONArray("keywords")
            val keywords = mutableListOf<String>()
            for (i in 0 until keywordsArray.length()) {
                keywords.add(keywordsArray.getString(i))
            }
            
            return AppCard(
                id = json.getString("id"),
                packageName = json.getString("packageName"),
                title = json.getString("title"),
                keywords = keywords,
                content = json.getString("content"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}