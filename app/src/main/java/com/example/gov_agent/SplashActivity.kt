package com.example.gov_agent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 显示启动屏幕
        setContent {
            SplashScreen {
                // 直接启动主活动
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    // 简单显示Logo，短暂延迟后跳转
    LaunchedEffect(key1 = true) {
        delay(500)  // 显示0.5秒后跳转
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // 直接显示图标，不添加动画效果
        Image(
            painter = painterResource(id = R.drawable.gov_agent_logo),
            contentDescription = "政务代理Logo",
            modifier = Modifier.size(250.dp)
        )
    }
} 