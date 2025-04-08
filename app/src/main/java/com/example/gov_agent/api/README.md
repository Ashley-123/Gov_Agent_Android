# 智能问答API使用指南

本文档介绍如何使用项目中的智能问答API接口，特别是流式响应功能。

## 流式API调用示例

### 1. 在ViewModel中使用

```kotlin
class YourViewModel : ViewModel() {
    private val chatRepository = ChatRepository()
    
    fun sendUserMessage(message: String) {
        viewModelScope.launch {
            // 使用流式API获取响应
            chatRepository.sendMessage(message).collect { content ->
                // 每次收到新内容时更新UI
                updateUI(content)
            }
        }
    }
}
```

### 2. 直接使用ApiClient

```kotlin
// 在协程作用域中使用
scope.launch {
    ApiClient.sendStreamingChatRequest("你的问题是什么？") { content, done ->
        // 每次收到内容时的回调
        println("收到内容: $content")
        
        if (done) {
            println("响应结束")
        }
    }
}
```

### 3. 非流式API（备用方案）

如果不需要流式响应，可以使用非流式API：

```kotlin
val response = ApiClient.sendChatRequest("你的问题是什么？")
println("收到完整响应: $response")
```

## 自定义配置

API请求参数可以在ApiClient.kt文件中修改，包括：

- 模型名称 (model)
- 温度 (temperature)
- 最大tokens (max_tokens)
- 其他LLM参数

## 错误处理

所有网络请求都包含错误处理，包括：
- 网络连接错误
- 超时
- 服务器错误

可以通过try-catch捕获这些错误，或在使用Flow时通过`catch`操作符处理。

## 性能优化

- 使用Flow进行流式处理，减少内存使用
- 使用OkHttp的连接池优化网络请求
- 适当的超时设置 