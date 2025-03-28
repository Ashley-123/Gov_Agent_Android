package com.example.gov_agent.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import android.util.Log
import kotlinx.coroutines.delay
import com.google.gson.Gson
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ChatRepository {
    private val gson = Gson()
    
    suspend fun sendMessage(message: String): Flow<String> = flow {
        try {
            Log.d("ChatRepository", "正在发送消息: $message")
            
            // 使用ApiClient发送请求
            try {
                val response = ApiClient.sendChatRequest(message)
                emit(response)
            } catch (e: IOException) {
                handleNetworkError(e, this)
            }
        } catch (e: SocketTimeoutException) {
            // 连接或读取超时
            Log.e("ChatRepository", "连接超时: ${e.message}")
            emit("连接服务器超时，请稍后重试")
            delay(500)
            emit("\n\n可能原因：\n1. 服务器响应时间过长\n2. 网络连接不稳定\n3. 服务器负载过高")
        } catch (e: Exception) {
            // 其他未预期的错误
            Log.e("ChatRepository", "未知错误: ${e.javaClass.simpleName} - ${e.message}")
            emit("发生错误：${e.javaClass.simpleName} - ${e.message}")
            delay(500)
            emit("\n\n如果问题持续存在，请联系技术支持。")
        }
    }
    
    private suspend fun handleNetworkError(e: IOException, emitter: kotlinx.coroutines.flow.FlowCollector<String>) {
        Log.e("ChatRepository", "网络异常: ${e.javaClass.simpleName} - ${e.message}", e)
        
        when (e) {
            is UnknownHostException -> {
                emitter.emit("连接服务器失败：无法解析服务器地址")
                delay(500)
                emitter.emit("\n\n可能原因：\n1. 您的网络连接不稳定\n2. 服务器暂时不可用\n3. 您的网络可能需要设置代理")
            }
            is java.net.ConnectException -> {
                if (e.message?.contains("Connection refused") == true) {
                    emitter.emit("连接服务器失败：服务器拒绝连接")
                    delay(500)
                    emitter.emit("\n\n可能原因：\n1. 服务器未运行或正在维护\n2. 服务端口未开放\n3. 防火墙阻止了连接")
                } else {
                    emitter.emit("连接服务器失败: ${e.message}")
                    delay(500)
                    emitter.emit("\n\n请检查您的网络连接并稍后重试。")
                }
            }
            is SocketTimeoutException -> {
                emitter.emit("连接服务器超时，请稍后重试")
                delay(500)
                emitter.emit("\n\n可能原因：\n1. 服务器响应时间过长\n2. 网络连接不稳定\n3. 服务器负载过高")
            }
            else -> {
                emitter.emit("网络请求失败: ${e.message}")
                delay(500)
                emitter.emit("\n\n请检查您的网络连接并稍后重试。")
            }
        }
    }
} 