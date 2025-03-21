package com.example.gov_agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.gov_agent.ui.theme.Gov_agentTheme
import com.example.gov_agent.ui.theme.MessageBubbleReceived
import com.example.gov_agent.ui.theme.MessageBubbleUser
import com.example.gov_agent.viewmodel.ChatViewModel
import com.example.gov_agent.api.ChatMessage
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gov_agentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GovAgentApp()
                }
            }
        }
    }
}

@Composable
fun GovAgentApp() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = "chat",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("chat") {
                ChatScreen()
            }
            composable("meeting") {
                MeetingRecordScreen(onNavigateToChat = { navController.navigate("chat") })
            }
            composable("profile") {
                ProfileScreen()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Chat : Screen("chat", "智能问答", Icons.Default.Home)
    object Meeting : Screen("meeting", "办事记录", Icons.Default.Mic)
    object Profile : Screen("profile", "个人中心", Icons.Default.Person)
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        Screen::class.sealedSubclasses.map { it.objectInstance!! }.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    var userInput by remember { mutableStateOf("") }
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 消息列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = scrollState,
            contentPadding = PaddingValues(16.dp)
        ) {
            items(messages) { message ->
                ChatMessageItem(message)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 自动滚动到底部
        LaunchedEffect(messages.size) {
            scope.launch {
                scrollState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
            }
        }

        // 输入区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("请输入您的问题...") },
                    enabled = !isLoading
                )

                IconButton(
                    onClick = {
                        if (userInput.isNotBlank()) {
                            viewModel.sendMessage(userInput)
                            userInput = ""
                        }
                    },
                    enabled = !isLoading && userInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (!isLoading && userInput.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

data class AudioRecord(
    val fileName: String,
    val duration: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PhotoRecord(
    val uri: Uri,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun MeetingRecordScreen(onNavigateToChat: () -> Unit) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    var audioRecords by remember { mutableStateOf(listOf<AudioRecord>()) }
    var photos by remember { mutableStateOf(listOf<PhotoRecord>()) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    
    val context = LocalContext.current
    
    // 相机启动器
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            photos = photos + PhotoRecord(currentPhotoUri!!)
            currentPhotoUri = null
        }
    }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            try {
                // 创建新文件并启动相机
                val file = createImageFile(context)
                currentPhotoUri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    file
                )
                cameraLauncher.launch(currentPhotoUri)
            } catch (e: Exception) {
                e.printStackTrace()
                // 处理异常
            }
        } else {
            // 权限被拒绝
        }
    }
    
    // 定时器效果
    LaunchedEffect(isRecording) {
        while(isRecording) {
            delay(1000)
            recordingTime += 1
        }
    }

    // 照片预览对话框
    if (selectedPhoto != null) {
        Dialog(
            onDismissRequest = { selectedPhoto = null }
        ) {
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .combinedClickable(
                        onClick = { selectedPhoto = null },
                        onLongClick = {
                            // 长按删除照片
                            photos = photos.filterNot { it.uri == selectedPhoto }
                            selectedPhoto = null
                        }
                    )
            ) {
                AsyncImage(
                    model = selectedPhoto,
                    contentDescription = "照片预览",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                
                Text(
                    text = "长按删除",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // 顶部栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                text = if (isRecording) "录音中..." else "准备就绪",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            
            Text(
                text = String.format("%02d:%02d", recordingTime / 60, recordingTime % 60),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRecording) Color.Red else Color(0xFF2196F3),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // 录音按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // 麦克风/停止按钮
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color.Red else Color(0xFF2196F3))
                    .clickable {
                        if (isRecording) {
                            // 停止录音
                            val duration = String.format("%02d:%02d", recordingTime / 60, recordingTime % 60)
                            audioRecords = audioRecords + AudioRecord(
                                fileName = "办事录音_${System.currentTimeMillis()}.mp3",
                                duration = duration
                            )
                            recordingTime = 0
                        }
                        isRecording = !isRecording
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_stop),
                        contentDescription = "停止录音",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "开始录音",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(32.dp))
            
            // 相机按钮
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2196F3))
                    .clickable {
                        try {
                            val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.READ_MEDIA_IMAGES
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            }
                            
                            if (hasPermissions(context, permissions)) {
                                // 已有权限，直接拍照
                                try {
                                    val file = createImageFile(context)
                                    currentPhotoUri = FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".provider",
                                        file
                                    )
                                    cameraLauncher.launch(currentPhotoUri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // 处理异常
                                }
                            } else {
                                // 请求权限
                                permissionLauncher.launch(permissions)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // 处理异常
                        }
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = "拍照",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // 记录预览
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "记录预览",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 音频记录列表
            LazyColumn {
                items(audioRecords) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_audio_file),
                                contentDescription = "音频文件",
                                tint = Color(0xFF2196F3)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(text = record.fileName)
                                Text(
                                    text = "时长: ${record.duration}",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play),
                                contentDescription = "播放",
                                tint = Color(0xFF2196F3)
                            )
                        }
                    }
                }
            }
            
            Text(
                text = "现场照片 (${photos.size}张)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 照片网格
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos) { photo ->
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "现场照片",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedPhoto = photo.uri
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            Text(
                text = "会议纪要",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 会议纪要内容
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = "1. 讨论了项目进度情况")
                    Text(text = "2. 确定了下一阶段工作计划")
                    Text(text = "3. 安排了具体任务分工")
                }
            }
        }
        
        // 上传按钮
        Button(
            onClick = { /* 上传操作 */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3)
            )
        ) {
            Text(
                text = "上传记录",
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // 顶部栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                text = "个人中心",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        
        // 用户信息卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 用户头像
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "政",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 用户信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = "政务工作人员",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: 10086",
                        color = Color.Gray
                    )
                }
            }
        }
        
        // 功能列表
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileMenuItem("我的任务", "共12项待处理任务")
                Divider()
                ProfileMenuItem("会议记录", "已保存15个会议记录")
                Divider()
                ProfileMenuItem("文件中心", "12个文件")
                Divider()
                ProfileMenuItem("工作日历", "本周有3个待办事项")
            }
        }
        
        // 设置卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileMenuItem("系统设置", "通知、隐私与安全")
                Divider()
                ProfileMenuItem("帮助中心", "常见问题与反馈")
                Divider()
                ProfileMenuItem("关于", "版本 1.0.0")
            }
        }
    }
}

@Composable
fun ProfileMenuItem(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_right),
            contentDescription = "打开",
            tint = Color.Gray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Gov_agentTheme {
        ChatScreen()
    }
}

private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    ).apply {
        // 确保文件目录存在
        parentFile?.mkdirs()
    }
}