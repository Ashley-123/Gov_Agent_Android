package com.example.gov_agent

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.json.JSONObject

/**
 * 网络请求工具类，支持HTTP和HTTPS请求
 */
object HttpClient {
    private const val TAG = "HttpClient"
    
    // 默认的连接超时时间（毫秒）
    private const val DEFAULT_CONNECT_TIMEOUT = 15000
    
    // 默认的读取超时时间（毫秒）
    private const val DEFAULT_READ_TIMEOUT = 15000
    
    // 信任所有证书的X509TrustManager
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })
    
    // 忽略主机名验证的HostnameVerifier
    private val allHostsValid = HostnameVerifier { _, _ -> true }
    
    /**
     * 执行简单的GET请求
     * @param urlString 请求地址
     * @param trustAllCertificates 是否信任所有证书
     * @return 响应内容
     */
    @Throws(IOException::class)
    fun get(urlString: String, trustAllCertificates: Boolean = false): String {
        val url = URL(urlString)
        val connection = createConnection(url, trustAllCertificates)
        
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = DEFAULT_CONNECT_TIMEOUT
            connection.readTimeout = DEFAULT_READ_TIMEOUT
            
            val responseCode = connection.responseCode
            return if (responseCode == HttpURLConnection.HTTP_OK) {
                readResponse(connection)
            } else {
                val errorMessage = readErrorResponse(connection)
                throw IOException("HTTP错误: $responseCode, $errorMessage")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 执行POST请求，发送JSON数据
     * @param urlString 请求地址
     * @param jsonData JSON数据
     * @param trustAllCertificates 是否信任所有证书
     * @return 响应内容
     */
    @Throws(IOException::class)
    fun postJson(urlString: String, jsonData: JSONObject, trustAllCertificates: Boolean = false): String {
        val url = URL(urlString)
        val connection = createConnection(url, trustAllCertificates)
        
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = DEFAULT_CONNECT_TIMEOUT
            connection.readTimeout = DEFAULT_READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            
            // 写入JSON数据
            DataOutputStream(connection.outputStream).use { outputStream ->
                outputStream.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            
            val responseCode = connection.responseCode
            return if (responseCode == HttpURLConnection.HTTP_OK) {
                readResponse(connection)
            } else {
                val errorMessage = readErrorResponse(connection)
                throw IOException("HTTP错误: $responseCode, $errorMessage")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 上传文件和表单数据
     * @param urlString 请求地址
     * @param formData 表单数据
     * @param fileField 文件字段名
     * @param file 文件
     * @param trustAllCertificates 是否信任所有证书
     * @return 响应内容
     */
    @Throws(IOException::class)
    fun uploadFile(
        urlString: String,
        formData: Map<String, String>,
        fileField: String,
        file: File,
        trustAllCertificates: Boolean = false
    ): String {
        val url = URL(urlString)
        val connection = createConnection(url, trustAllCertificates)
        val boundary = "---------------------------" + System.currentTimeMillis()
        
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = DEFAULT_CONNECT_TIMEOUT
            connection.readTimeout = DEFAULT_READ_TIMEOUT * 2 // 上传文件需要更长的超时时间
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            DataOutputStream(connection.outputStream).use { outputStream ->
                // 添加JSON数据
                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"data\"\r\n")
                outputStream.writeBytes("Content-Type: application/json; charset=utf-8\r\n")
                outputStream.writeBytes("\r\n")
                outputStream.writeBytes(formData["data"] + "\r\n")
                
                // 添加文件数据
                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"$fileField\"; filename=\"${file.name}\"\r\n")
                val mimeType = when (fileField) {
                    "photos" -> "image/jpeg"
                    "audios" -> "audio/mpeg"
                    else -> getMimeType(file.name)
                }
                outputStream.writeBytes("Content-Type: $mimeType\r\n")
                outputStream.writeBytes("\r\n")
                
                // 写入文件内容
                FileInputStream(file).use { inputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                
                outputStream.writeBytes("\r\n")
                outputStream.writeBytes("--$boundary--\r\n")
                outputStream.flush()
            }
            
            val responseCode = connection.responseCode
            return if (responseCode == HttpURLConnection.HTTP_OK) {
                readResponse(connection)
            } else {
                val errorMessage = readErrorResponse(connection)
                throw IOException("HTTP错误: $responseCode, $errorMessage")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 上传多个文件和JSON数据
     */
    @Throws(IOException::class)
    fun uploadMultipleFiles(
        urlString: String,
        jsonData: String,
        photos: List<File>,
        audios: List<File>,
        trustAllCertificates: Boolean = false
    ): String {
        val url = URL(urlString)
        val connection = createConnection(url, trustAllCertificates)
        val boundary = "---------------------------" + System.currentTimeMillis()
        
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = DEFAULT_CONNECT_TIMEOUT
            connection.readTimeout = DEFAULT_READ_TIMEOUT * 2
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            DataOutputStream(connection.outputStream).use { outputStream ->
                // 添加JSON数据
                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"data\"\r\n")
                outputStream.writeBytes("Content-Type: application/json; charset=utf-8\r\n")
                outputStream.writeBytes("\r\n")
                outputStream.writeBytes(jsonData + "\r\n")
                
                // 添加照片文件
                photos.forEach { file ->
                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"photos\"; filename=\"${file.name}\"\r\n")
                    outputStream.writeBytes("Content-Type: image/jpeg\r\n")
                    outputStream.writeBytes("\r\n")
                    
                    FileInputStream(file).use { input ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    outputStream.writeBytes("\r\n")
                }
                
                // 添加音频文件
                audios.forEach { file ->
                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"audios\"; filename=\"${file.name}\"\r\n")
                    outputStream.writeBytes("Content-Type: audio/mpeg\r\n")
                    outputStream.writeBytes("\r\n")
                    
                    FileInputStream(file).use { input ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    outputStream.writeBytes("\r\n")
                }
                
                // 写入结束标记
                outputStream.writeBytes("--$boundary--\r\n")
                outputStream.flush()
            }
            
            val responseCode = connection.responseCode
            return if (responseCode == HttpURLConnection.HTTP_OK) {
                readResponse(connection)
            } else {
                val errorMessage = readErrorResponse(connection)
                throw IOException("HTTP错误: $responseCode, $errorMessage")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 创建适当的HTTP连接
     * @param url 请求URL
     * @param trustAllCertificates 是否信任所有证书
     * @return HTTP连接
     */
    @Throws(IOException::class)
    private fun createConnection(url: URL, trustAllCertificates: Boolean): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        
        // 如果是HTTPS连接并且需要信任所有证书
        if (connection is HttpsURLConnection && trustAllCertificates) {
            try {
                // 创建SSLContext
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                
                // 设置SSL工厂
                connection.sslSocketFactory = sslContext.socketFactory
                connection.hostnameVerifier = allHostsValid
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException, is KeyManagementException -> {
                        Log.e(TAG, "配置SSL失败", e)
                    }
                    else -> throw e
                }
            }
        }
        
        return connection
    }
    
    /**
     * 读取HTTP响应内容
     * @param connection HTTP连接
     * @return 响应内容
     */
    @Throws(IOException::class)
    private fun readResponse(connection: HttpURLConnection): String {
        BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            return response.toString()
        }
    }
    
    /**
     * 读取HTTP错误响应
     * @param connection HTTP连接
     * @return 错误响应内容
     */
    private fun readErrorResponse(connection: HttpURLConnection): String {
        val errorStream = connection.errorStream ?: return "未知错误"
        return BufferedReader(InputStreamReader(errorStream)).use { reader ->
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            response.toString()
        }
    }
    
    /**
     * 根据文件名获取MIME类型
     * @param fileName 文件名
     * @return MIME类型
     */
    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".mp3", true) -> "audio/mpeg"
            fileName.endsWith(".wav", true) -> "audio/wav"
            fileName.endsWith(".mp4", true) -> "video/mp4"
            fileName.endsWith(".json", true) -> "application/json"
            fileName.endsWith(".txt", true) -> "text/plain"
            fileName.endsWith(".pdf", true) -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
} 