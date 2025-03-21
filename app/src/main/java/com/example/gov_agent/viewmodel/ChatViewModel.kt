package com.example.gov_agent.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gov_agent.api.ChatMessage
import com.example.gov_agent.api.ChatRepository
import kotlinx.coroutines.launch
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

class ChatViewModel : ViewModel() {
    private val chatRepository = ChatRepository(
        WebClient.builder()
            .baseUrl("http://47.98.99.126:14425")
            .clientConnector(
                ReactorClientHttpConnector(
                    HttpClient.create(
                        ConnectionProvider.builder("custom")
                            .maxConnections(500)
                            .maxIdleTime(Duration.ofSeconds(20))
                            .maxLifeTime(Duration.ofSeconds(60))
                            .pendingAcquireTimeout(Duration.ofSeconds(60))
                            .build()
                    )
                    .responseTimeout(Duration.ofSeconds(30))
                    .keepAlive(true)
                    .compress(true)
                )
            )
            .codecs { configurer -> 
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)
            }
            .build()
    )
    
    // 聊天消息列表
    val messages = mutableStateListOf<ChatMessage>()
    
    // 加载状态
    val isLoading = mutableStateOf(false)
    
    init {
        // 初始化欢迎消息
        messages.add(ChatMessage("您好！我是您的智能助手，请问有什么可以帮您？", false))
    }
    
    // 发送用户消息
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            // 添加用户消息
            messages.add(ChatMessage(message, true))
            isLoading.value = true
            
            try {
                // 创建一个临时的助手消息
                val assistantMessage = ChatMessage("", false)
                messages.add(assistantMessage)
                
                // 收集流式响应
                chatRepository.sendMessage(message).collect { content ->
                    // 更新最后一条消息的内容
                    val lastIndex = messages.lastIndex
                    messages[lastIndex] = messages[lastIndex].copy(
                        content = messages[lastIndex].content + content
                    )
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.SocketTimeoutException -> 
                        "连接服务器超时，请检查网络连接并稍后重试"
                    is java.net.ConnectException -> 
                        "无法连接到服务器，请确认网络连接正常"
                    else -> "发生错误：${e.message}"
                }
                messages.add(ChatMessage(errorMessage, false))
            } finally {
                isLoading.value = false
            }
        }
    }
} 