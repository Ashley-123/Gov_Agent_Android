package com.example.gov_agent.api

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import org.json.JSONObject

object ApiClient {
    private const val BASE_URL = "http://js2.blockelite.cn:14425"
    private const val CHAT_ENDPOINT = "/api/chat"
    private val gson = Gson()
    
    // 系统提示词
    private const val SYSTEM_PROMPT = """你是一个专为雨湖区服务的智能助手，名叫"雨湖智能助手"。
你的任务是回答与雨湖区相关的问题，提供雨湖区的政策、办事指南、常见问题解答等信息。所有回答必须限定在雨湖区的范围内。如果用户的问题超出雨湖区，请礼貌地说明你只服务于雨湖区，并建议用户咨询其他相关机构。"""
    
    /**
     * 使用HttpURLConnection发送聊天请求
     * @param userMessage 用户消息文本
     * @return 服务器返回的响应文本
     */
    suspend fun sendChatRequest(userMessage: String): String = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL$CHAT_ENDPOINT")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            // 设置连接参数
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            // 添加系统提示词到用户消息
            val fullMessage = "$SYSTEM_PROMPT\n\n用户问题：$userMessage"
            
            // 准备请求数据
            val requestBody = JSONObject().apply {
                put("model", "deepseek-r1:32b")
                put("stream", false)
                
                // 创建消息数组
                val messagesArray = org.json.JSONArray().apply {
                    // 添加系统消息
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    
                    // 添加用户消息
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                }
                put("messages", messagesArray)
                
                // 添加选项
                val optionsObj = JSONObject().apply {
                    put("temperature", 0.7)
                    put("top_p", 0.9)
                    put("max_tokens", 4000)
                }
                put("options", optionsObj)
            }
            
            // 发送请求
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            // 检查响应状态
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 读取响应
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                
                // 解析JSON响应，提取消息内容
                val jsonResponse = JSONObject(response.toString())
                val message = jsonResponse.getJSONObject("message")
                val content = message.getString("content")
                
                return@withContext content
            } else {
                // 处理错误
                throw Exception("API请求失败，状态码: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
} 