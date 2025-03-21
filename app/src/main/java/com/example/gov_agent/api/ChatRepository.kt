package com.example.gov_agent.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToFlux
import android.util.Log
import kotlinx.coroutines.delay

class ChatRepository(private val webClient: WebClient) {
    
    suspend fun sendMessage(message: String): Flow<String> = flow {
        try {
            Log.d("ChatRepository", "正在发送消息: $message")
            
            val request = ChatRequest(
                model = "deepseek-r1:32b",
                messages = listOf(ChatRequestMessage("user", message)),
                stream = true,
                options = ChatRequestOptions(temperature = 0.7, max_tokens = 2000)
            )

            val response = webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux<String>()
                .asFlow()

            var receivedContent = false
            response.collect { line ->
                Log.d("ChatRepository", "收到响应: $line")
                receivedContent = true
                line.parseChatResponse()?.let { content ->
                    emit(content)
                }
            }
            
            if (!receivedContent) {
                emit("服务器未返回有效响应，请稍后重试")
            }
        } catch (e: WebClientResponseException) {
            Log.e("ChatRepository", "API错误: ${e.statusCode} - ${e.message}")
            emit("API请求失败: ${e.statusCode} - ${e.message}")
        } catch (e: java.net.UnknownHostException) {
            Log.e("ChatRepository", "域名解析失败: ${e.message}")
            emit("无法连接到服务器(${e.message})，请检查网络连接")
            delay(500)
            emit("\n\n可能原因：\n1. 您的网络连接不稳定\n2. 服务器域名无法解析\n3. 防火墙或网络限制阻止了连接")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("ChatRepository", "连接超时: ${e.message}")
            emit("连接服务器超时，请稍后重试")
        } catch (e: Exception) {
            Log.e("ChatRepository", "未知错误: ${e.javaClass.simpleName} - ${e.message}")
            emit("发生错误：${e.javaClass.simpleName} - ${e.message}")
        }
    }
} 