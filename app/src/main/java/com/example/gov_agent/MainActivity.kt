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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissState
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.example.gov_agent.audio.AudioPlayer
import com.example.gov_agent.audio.AudioRecorder
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import org.json.JSONArray
import org.json.JSONObject

// 会议记录数据类
data class MeetingRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val summary: List<String>,
    val audioRecords: List<AudioRecord>,
    val photos: List<PhotoRecord>,
    val timestamp: Long = System.currentTimeMillis()
)

// 添加应用级共享状态
object AppState {
    val meetingRecords = mutableStateListOf<MeetingRecord>()

    // 添加本地存储相关方法
    fun saveMeetingRecordsToLocal(context: Context) {
        try {
            val sharedPrefs = context.getSharedPreferences("gov_agent_prefs", Context.MODE_PRIVATE)
            val jsonList = meetingRecords.map { record ->
                // 将复杂对象简化为可序列化的版本
                val simplifiedRecord = mapOf(
                    "id" to record.id,
                    "title" to record.title,
                    "date" to record.date,
                    "summary" to record.summary,
                    "timestamp" to record.timestamp,
                    "audioCount" to record.audioRecords.size,
                    "photoCount" to record.photos.size
                )
                JSONObject(simplifiedRecord).toString()
            }
            
            sharedPrefs.edit().putString("meeting_records", JSONArray(jsonList).toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMeetingRecordsFromLocal(context: Context) {
        try {
            val sharedPrefs = context.getSharedPreferences("gov_agent_prefs", Context.MODE_PRIVATE)
            val jsonString = sharedPrefs.getString("meeting_records", null) ?: return
            
            val jsonArray = JSONArray(jsonString)
            val loadedRecords = (0 until jsonArray.length()).map { i ->
                val recordJson = JSONObject(jsonArray.getString(i))
                
                // 从JSON创建简化版的MeetingRecord
                val summaryArray = recordJson.getJSONArray("summary")
                val summaryList = (0 until summaryArray.length()).map { j ->
                    summaryArray.getString(j)
                }
                
                MeetingRecord(
                    id = recordJson.getString("id"),
                    title = recordJson.getString("title"),
                    date = recordJson.getString("date"),
                    summary = summaryList,
                    audioRecords = emptyList(), // 简化版不保存实际录音
                    photos = emptyList(), // 简化版不保存实际照片
                    timestamp = recordJson.getLong("timestamp")
                )
            }
            
            // 更新内存中的记录
            meetingRecords.clear()
            meetingRecords.addAll(loadedRecords)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 加载本地存储的会议记录
        AppState.loadMeetingRecordsFromLocal(this)
        
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

// 添加NavController的CompositionLocal
val LocalNavController = compositionLocalOf<NavHostController> { error("No NavController provided") }

@Composable
fun GovAgentApp() {
    val navController = rememberNavController()
    
    // 使用CompositionLocalProvider提供NavController给所有子组件
    CompositionLocalProvider(LocalNavController provides navController) {
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
    val file: File,
    val fileName: String,
    val duration: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PhotoRecord(
    val uri: Uri,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingRecordScreen(onNavigateToChat: () -> Unit) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    var audioRecords by remember { mutableStateOf(listOf<AudioRecord>()) }
    var photos by remember { mutableStateOf(listOf<PhotoRecord>()) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    var currentPlayingIndex by remember { mutableStateOf<Int?>(null) }
    var meetingSummary by remember { mutableStateOf(listOf("讨论了项目进度情况", "确定了下一阶段工作计划", "安排了具体任务分工")) }
    
    // 上传状态
    var isUploading by remember { mutableStateOf(false) }
    var showUploadSuccess by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    // 使用在GovAgentApp中创建的共享NavController，而不是创建新的
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val audioRecorder = remember { AudioRecorder(context) }
    val audioPlayer = remember { AudioPlayer(context) }
    val scrollState = rememberScrollState()
    
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
            if (permissions.containsKey(Manifest.permission.RECORD_AUDIO)) {
                // 录音权限已授予，开始录音
                isRecording = true
                audioRecorder.startRecording()
            } else {
                try {
                    // 相机权限已授予，开始拍照
                    val file = createImageFile(context)
                    currentPhotoUri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".provider",
                        file
                    )
                    cameraLauncher.launch(currentPhotoUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    // 定时器效果
    LaunchedEffect(isRecording) {
        recordingTime = 0
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
    
    // 上传成功对话框
    if (showUploadSuccess) {
        AlertDialog(
            onDismissRequest = { 
                showUploadSuccess = false
            },
            title = { Text("上传成功") },
            text = { Text("办事记录已成功上传到个人中心") },
            confirmButton = {
                Button(
                    onClick = { 
                        showUploadSuccess = false
                        // 安全地导航到个人中心
                        navController.navigate(Screen.Profile.route) {
                            // 避免创建多个页面实例
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // 顶部栏 (固定不滚动)
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
        
        // 可滚动的内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
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
                                val result = audioRecorder.stopRecording()
                                if (result != null) {
                                    val (file, durationMs) = result
                                    file?.let {
                                        val duration = String.format("%02d:%02d", durationMs / 1000 / 60, (durationMs / 1000) % 60)
                                        audioRecords = audioRecords + AudioRecord(
                                            file = it,
                                            fileName = "办事录音_${System.currentTimeMillis()}.mp3",
                                            duration = duration
                                        )
                                    }
                                }
                                isRecording = false
                            } else {
                                // 请求录音权限
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                                    == PackageManager.PERMISSION_GRANTED) {
                                    // 已有权限，开始录音
                                    audioRecorder.startRecording()
                                    isRecording = true
                                } else {
                                    // 请求权限
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                }
                            }
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
                                    }
                                } else {
                                    // 请求权限
                                    permissionLauncher.launch(permissions)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
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
                if (audioRecords.isNotEmpty()) {
                    Column {
                        audioRecords.forEachIndexed { index, record ->
                            var offsetX by remember { mutableStateOf(0f) }
                            val scope = rememberCoroutineScope()
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                // 红色删除按钮背景（固定在右侧）
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(160.dp)
                                        .height(84.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Red)
                                        .clickable {
                                            if (offsetX < -80) {
                                                // 删除录音
                                                audioRecords = audioRecords.filterNot { it == record }
                                                // 如果正在播放这个录音，停止播放
                                                if (currentPlayingIndex == index) {
                                                    audioPlayer.stopPlaying()
                                                    currentPlayingIndex = null
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(60.dp)
                                            .padding(end = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                
                                // 录音卡片（带自定义滑动）
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                                        .draggable(
                                            orientation = Orientation.Horizontal,
                                            state = rememberDraggableState { delta ->
                                                // 限制最大滑动距离为160dp
                                                val newOffset = (offsetX + delta).coerceIn(-160f, 0f)
                                                offsetX = newOffset
                                            },
                                            onDragStopped = {
                                                // 根据滑动速度和距离决定是否自动回弹
                                                if (offsetX > -80f) {
                                                    // 右滑回弹到原位
                                                    scope.launch {
                                                        offsetX = 0f
                                                    }
                                                } else {
                                                    // 左滑停留在删除按钮处
                                                    scope.launch {
                                                        offsetX = -160f
                                                    }
                                                }
                                            }
                                        )
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
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
                                            
                                            // 播放按钮
                                            Icon(
                                                painter = if (currentPlayingIndex == index) 
                                                    painterResource(id = R.drawable.ic_stop) 
                                                else 
                                                    painterResource(id = R.drawable.ic_play),
                                                contentDescription = if (currentPlayingIndex == index) "停止" else "播放",
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.clickable {
                                                    if (currentPlayingIndex == index) {
                                                        // 停止播放
                                                        audioPlayer.stopPlaying()
                                                        currentPlayingIndex = null
                                                    } else {
                                                        // 开始播放
                                                        audioPlayer.playFile(record.file) {
                                                            // 播放完成后回调
                                                            currentPlayingIndex = null
                                                        }
                                                        currentPlayingIndex = index
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 显示无录音提示
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无录音记录",
                                color = Color.Gray
                            )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
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
                    
                    // 如果没有照片，显示提示
                    if (photos.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无照片",
                                    color = Color.Gray
                                )
                            }
                        }
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
                        meetingSummary.forEachIndexed { index, item ->
                            Text(text = "${index + 1}. $item")
                        }
                    }
                }
                
                // 上传按钮
                Button(
                    onClick = { 
                        // 创建会议记录对象
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val currentDate = dateFormat.format(Date())
                        
                        val meetingRecord = MeetingRecord(
                            title = "办事记录 $currentDate",
                            date = currentDate,
                            summary = meetingSummary,
                            audioRecords = audioRecords,
                            photos = photos
                        )
                        
                        // 模拟上传过程
                        isUploading = true
                        
                        // 使用协程延迟模拟网络请求
                        scope.launch {
                            delay(1500) // 模拟1.5秒的上传时间
                            // 将会议记录添加到共享状态
                            AppState.meetingRecords.add(meetingRecord)
                            // 保存到本地存储
                            AppState.saveMeetingRecordsToLocal(context)
                            isUploading = false
                            showUploadSuccess = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    enabled = !isUploading
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isUploading) "上传中..." else "上传记录",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen() {
    var selectedMeetingRecord by remember { mutableStateOf<MeetingRecord?>(null) }
    val meetingRecords = remember { AppState.meetingRecords }
    
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
                        text = "ID: 12345",
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
                ProfileMenuItem(
                    title = "办事历史", 
                    subtitle = "已保存${meetingRecords.size}条办事记录",
                    onClick = {
                        // 这里可以导航到办事历史列表页面
                    }
                )
                
                // 显示上传的办事记录
                if (meetingRecords.isNotEmpty()) {
                    Padding(padding = PaddingValues(horizontal = 16.dp)) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(200.dp)
                        ) {
                            items(meetingRecords) { record ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { selectedMeetingRecord = record },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = record.title,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "日期: ${record.date}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "录音: ${record.audioRecords.size}个 | 照片: ${record.photos.size}张",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
    
    // 会议记录详情对话框
    if (selectedMeetingRecord != null) {
        Dialog(onDismissRequest = { selectedMeetingRecord = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = selectedMeetingRecord!!.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "日期: ${selectedMeetingRecord!!.date}",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "办事记录:",
                        fontWeight = FontWeight.Bold
                    )
                    selectedMeetingRecord!!.summary.forEachIndexed { index, item ->
                        Text(text = "${index + 1}. $item")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (selectedMeetingRecord!!.photos.isNotEmpty()) {
                        Text(
                            text = "现场照片 (${selectedMeetingRecord!!.photos.size}张):",
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedMeetingRecord!!.photos) { photo ->
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = "现场照片",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    if (selectedMeetingRecord!!.audioRecords.isNotEmpty()) {
                        Text(
                            text = "录音文件 (${selectedMeetingRecord!!.audioRecords.size}个):",
                            fontWeight = FontWeight.Bold
                        )
                        Column {
                            selectedMeetingRecord!!.audioRecords.forEach { record ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
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
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = record.fileName,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "时长: ${record.duration}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { selectedMeetingRecord = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    title: String, 
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

@Composable
fun Padding(
    padding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.padding(padding)
    ) {
        content()
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