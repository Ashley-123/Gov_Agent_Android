package com.example.gov_agent.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gov_agent.api.ChatMessage
import com.example.gov_agent.api.ChatRepository
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.TimeUnit

class ChatViewModel : ViewModel() {
    // 使用无参构造函数创建ChatRepository
    private val chatRepository = ChatRepository()
    
    // 聊天消息列表
    val messages = mutableStateListOf<ChatMessage>()
    
    // 加载状态
    val isLoading = mutableStateOf(false)
    
    // 连接测试状态
    val isConnectionTesting = mutableStateOf(false)
    val connectionTestResult = mutableStateOf<String?>(null)
    
    init {
        // 初始化欢迎消息
        messages.add(ChatMessage("您好！我是您的智能助手，请问有什么可以帮您？", false))
        
        // 预热DNS解析，但不阻塞UI线程
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "预热DNS解析...")
                java.net.InetAddress.getAllByName("js2.blockelite.cn")
                Log.d("ChatViewModel", "DNS解析成功")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "DNS预热失败", e)
            }
        }
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
                val assistantMessage = ChatMessage("", false, isLoading = true)
                messages.add(assistantMessage)
                
                // 收集流式响应
                chatRepository.sendMessage(message).collect { content ->
                    // 更新最后一条消息的内容
                    val lastIndex = messages.lastIndex
                    messages[lastIndex] = messages[lastIndex].copy(
                        content = messages[lastIndex].content + content,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "发送消息失败", e)
                val errorMessage = when (e) {
                    is java.net.SocketTimeoutException -> 
                        "连接服务器超时，请检查网络连接并稍后重试"
                    is java.net.ConnectException -> 
                        "无法连接到服务器，请确认网络连接正常"
                    is java.net.UnknownHostException ->
                        "无法解析服务器地址，请检查网络连接或DNS设置"
                    else -> "发生错误：${e.message}"
                }
                messages.add(ChatMessage(errorMessage, false))
            } finally {
                isLoading.value = false
            }
        }
    }
} 