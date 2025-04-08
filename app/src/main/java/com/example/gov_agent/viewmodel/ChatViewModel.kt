package com.example.gov_agent.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gov_agent.api.ChatMessage
import com.example.gov_agent.api.ChatRepository
import com.example.gov_agent.api.ChatHistory
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
    
    // 对话历史列表
    val chatHistories = mutableStateListOf<ChatHistory>()
    
    // 当前对话ID
    private var currentChatId: String? = null
    
    // 加载状态
    val isLoading = mutableStateOf(false)
    
    // 连接测试状态
    val isConnectionTesting = mutableStateOf(false)
    val connectionTestResult = mutableStateOf<String?>(null)
    
    init {
        // 初始化欢迎消息
        startNewChat()
        
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
    
    // 开始新对话
    fun startNewChat() {
        currentChatId = java.util.UUID.randomUUID().toString()
        messages.clear()
        messages.add(ChatMessage("您好！我是您的智能助手，请问有什么可以帮您？", false))
        // 保存新对话到历史记录
        saveCurrentChat()
    }
    
    // 加载历史对话
    fun loadChatHistory(chatHistory: ChatHistory) {
        currentChatId = chatHistory.id
        messages.clear()
        messages.addAll(chatHistory.messages)
    }
    
    // 保存当前对话到历史记录
    private fun saveCurrentChat() {
        if (messages.isEmpty()) return
        
        val title = messages.firstOrNull { it.isUser }?.content?.take(20) ?: "新对话"
        val lastMessage = messages.lastOrNull()?.content ?: ""
        
        val chatHistory = ChatHistory(
            id = currentChatId ?: java.util.UUID.randomUUID().toString(),
            title = title,
            messages = messages.toList(),
            lastMessage = lastMessage
        )
        
        // 更新或添加历史记录
        val existingIndex = chatHistories.indexOfFirst { it.id == chatHistory.id }
        if (existingIndex >= 0) {
            chatHistories[existingIndex] = chatHistory
        } else {
            chatHistories.add(chatHistory)
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
                
                // 保存对话到历史记录
                saveCurrentChat()
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
                saveCurrentChat()
            } finally {
                isLoading.value = false
            }
        }
    }
    
    // 删除历史对话
    fun deleteChatHistory(chatId: String) {
        chatHistories.removeAll { it.id == chatId }
    }
} 