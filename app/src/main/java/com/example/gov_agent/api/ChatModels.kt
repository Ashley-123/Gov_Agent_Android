package com.example.gov_agent.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

// 请求模型
data class ChatRequestMessage(
    val role: String,
    val content: String
)

data class ChatRequestOptions(
    val temperature: Double,
    val max_tokens: Int
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val stream: Boolean,
    val options: ChatRequestOptions
)

// 用于解析流响应的模型
data class ChatResponseContent(
    val content: String
)

data class ChatResponseMessage(
    val message: ChatResponseContent
)

// 用于UI显示的消息模型
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val id: String = java.util.UUID.randomUUID().toString(),
    val isLoading: Boolean = false
)

// 用于解析流响应的扩展函数
fun String.parseChatResponse(): String? {
    return try {
        val json = Gson().fromJson(this, JsonObject::class.java)
        if (json.has("message") && json.getAsJsonObject("message").has("content")) {
            json.getAsJsonObject("message").get("content").asString
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
} 