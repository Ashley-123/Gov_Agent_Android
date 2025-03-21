package com.example.gov_agent.api

import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Flux

object ApiClient {
    private const val BASE_URL = "http://js2.blockelite.cn:14425"
    
    // 创建WebClient实例
    val webClient: WebClient = WebClient.builder()
        .baseUrl(BASE_URL)
        .build()
} 