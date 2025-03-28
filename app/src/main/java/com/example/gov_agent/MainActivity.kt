package com.example.gov_agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import java.net.URL
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
import androidx.compose.foundation.horizontalScroll
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import android.util.Log
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.ExperimentalFoundationApi

// 会议记录数据类
data class MeetingRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val summary: String, // Markdown格式的会议纪要
    val transcript: String? = null, // 语音识别的文本内容
    val fileUrl: String? = null, // 文件访问URL
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
            val recordsList = mutableListOf<JSONObject>()
            
            meetingRecords.forEach { record ->
                // 将音频文件复制到应用的永久存储目录
                val permanentAudioRecords = record.audioRecords.map { audioRecord ->
                    val permanentFile = copyFileToAppStorage(context, audioRecord.file, "audio")
                    AudioRecord(
                        file = permanentFile,
                        fileName = audioRecord.fileName,
                        duration = audioRecord.duration,
                        timestamp = audioRecord.timestamp
                    )
                }
                
                // 将图片文件复制到应用的永久存储目录
                val permanentPhotoRecords = record.photos.map { photoRecord ->
                    val permanentUri = copyImageToAppStorage(context, photoRecord.uri)
                    PhotoRecord(
                        uri = permanentUri,
                        timestamp = photoRecord.timestamp
                    )
                }
                
                // 创建包含永久存储文件路径的记录对象
                val permanentRecord = MeetingRecord(
                    id = record.id,
                    title = record.title,
                    date = record.date,
                    summary = record.summary,
                    transcript = record.transcript,
                    fileUrl = record.fileUrl,
                    audioRecords = permanentAudioRecords,
                    photos = permanentPhotoRecords,
                    timestamp = record.timestamp
                )
                
                // 序列化记录
                val recordJson = JSONObject().apply {
                    put("id", permanentRecord.id)
                    put("title", permanentRecord.title)
                    put("date", permanentRecord.date)
                    put("timestamp", permanentRecord.timestamp)
                    
                    // 保存摘要
                    put("summary", permanentRecord.summary)
                    
                    // 保存转录文本和文件URL（如果有）
                    permanentRecord.transcript?.let { put("transcript", it) }
                    permanentRecord.fileUrl?.let { put("file_url", it) }
                    
                    // 保存音频文件路径
                    val audioArray = JSONArray()
                    permanentRecord.audioRecords.forEach { audio ->
                        val audioJson = JSONObject().apply {
                            put("filePath", audio.file.absolutePath)
                            put("fileName", audio.fileName)
                            put("duration", audio.duration)
                            put("timestamp", audio.timestamp)
                        }
                        audioArray.put(audioJson)
                    }
                    put("audioRecords", audioArray)
                    
                    // 保存图片文件URI
                    val photoArray = JSONArray()
                    permanentRecord.photos.forEach { photo ->
                        val photoJson = JSONObject().apply {
                            put("uriString", photo.uri.toString())
                            put("timestamp", photo.timestamp)
                        }
                        photoArray.put(photoJson)
                    }
                    put("photos", photoArray)
                }
                recordsList.add(recordJson)
            }
            
            sharedPrefs.edit().putString("meeting_records", JSONArray(recordsList).toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMeetingRecordsFromLocal(context: Context) {
        try {
            val sharedPrefs = context.getSharedPreferences("gov_agent_prefs", Context.MODE_PRIVATE)
            val jsonString = sharedPrefs.getString("meeting_records", null) ?: return
            
            val jsonArray = JSONArray(jsonString)
            val loadedRecords = mutableListOf<MeetingRecord>()
            
            for (i in 0 until jsonArray.length()) {
                val recordJson = jsonArray.getJSONObject(i)
                
                // 解析音频记录
                val audioArray = recordJson.optJSONArray("audioRecords") ?: JSONArray()
                val audioRecords = mutableListOf<AudioRecord>()
                
                for (j in 0 until audioArray.length()) {
                    val audioJson = audioArray.getJSONObject(j)
                    val filePath = audioJson.getString("filePath")
                    val file = File(filePath)
                    if (file.exists()) {
                        audioRecords.add(
                            AudioRecord(
                                file = file,
                                fileName = audioJson.getString("fileName"),
                                duration = audioJson.getString("duration"),
                                timestamp = audioJson.getLong("timestamp")
                            )
                        )
                    }
                }
                
                // 解析照片记录
                val photoArray = recordJson.optJSONArray("photos") ?: JSONArray()
                val photoRecords = mutableListOf<PhotoRecord>()
                
                for (j in 0 until photoArray.length()) {
                    val photoJson = photoArray.getJSONObject(j)
                    val uriString = photoJson.getString("uriString")
                    try {
                        val uri = Uri.parse(uriString)
                        photoRecords.add(
                            PhotoRecord(
                                uri = uri,
                                timestamp = photoJson.getLong("timestamp")
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                loadedRecords.add(
                    MeetingRecord(
                        id = recordJson.getString("id"),
                        title = recordJson.getString("title"),
                        date = recordJson.getString("date"),
                        summary = recordJson.getString("summary"),
                        transcript = recordJson.optString("transcript", null),
                        fileUrl = recordJson.optString("file_url", null),
                        audioRecords = audioRecords,
                        photos = photoRecords,
                        timestamp = recordJson.getLong("timestamp")
                    )
                )
            }
            
            // 更新内存中的记录
            meetingRecords.clear()
            meetingRecords.addAll(loadedRecords)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // 辅助方法：将文件复制到应用永久存储目录
    private fun copyFileToAppStorage(context: Context, sourceFile: File, subFolder: String): File {
        val destinationDir = File(context.filesDir, subFolder).apply { mkdirs() }
        val destinationFile = File(destinationDir, "${System.currentTimeMillis()}_${sourceFile.name}")
        
        try {
            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            return sourceFile // 如果复制失败，返回原文件
        }
    }
    
    // 辅助方法：将图片复制到应用永久存储目录
    private fun copyImageToAppStorage(context: Context, sourceUri: Uri): Uri {
        try {
            val destinationDir = File(context.filesDir, "images").apply { mkdirs() }
            val fileName = "${System.currentTimeMillis()}.jpg"
            val destinationFile = File(destinationDir, fileName)
            
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 创建永久存储的Uri
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                destinationFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return sourceUri // 如果复制失败，返回原URI
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 加载本地存储的会议记录
        AppState.loadMeetingRecordsFromLocal(this)
        
        // 设置软键盘不会遮挡内容
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
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
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding() // 整体跟随键盘
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
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .clickable { focusManager.clearFocus() }
                    ) {
                        ChatMessageItem(message)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // 自动滚动到底部
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    scrollState.animateScrollToItem(messages.lastIndex)
                }
            }
            
            // 控制输入框与键盘之间的间距 - 只有在键盘可见时添加间距
            if (imeVisible) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 输入区域
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
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
                            .padding(end = 8.dp)
                            .focusRequester(focusRequester),
                        placeholder = { Text("请输入您的问题...") },
                        enabled = !isLoading,
                        maxLines = 3 // 限制最大行数，防止输入框太大
                    )
                    
                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                viewModel.sendMessage(userInput)
                                userInput = ""
                                // 发送消息后立即滚动到底部
                                scope.launch {
                                    delay(100) // 短暂延迟等待UI更新
                                    if (messages.isNotEmpty()) {
                                        scrollState.animateScrollToItem(messages.size - 1)
                                    }
                                }
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
    
    // 检查消息是否包含<think>标签
    val hasThinkContent = !message.isUser && message.content.contains("<think>") && message.content.contains("</think>")
    
    // 解析消息内容，分离<think>标签内的内容
    val (thinkContent, normalContent) = if (hasThinkContent) {
        val thinkStart = message.content.indexOf("<think>")
        val thinkEnd = message.content.indexOf("</think>") + 8 // 8是"</think>"的长度
        val thinkText = message.content.substring(thinkStart, thinkEnd)
        val normalText = message.content.replace(thinkText, "").trim()
        
        // 提取<think>标签中的纯文本内容
        val extractedThinkContent = thinkText.removePrefix("<think>").removeSuffix("</think>").trim()
        
        Pair(extractedThinkContent, normalText)
    } else {
        Pair("", message.content)
    }
    
    // 控制<think>内容的展开状态
    var isThinkExpanded by remember { mutableStateOf(false) }
    // 状态记忆
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    
    // 显示snackbar的作用域
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // 添加按钮显示状态
    var shouldShowButtons by remember { mutableStateOf(false) }
    
    // 使用LaunchedEffect在AI回复完成后显示按钮
    LaunchedEffect(message.id) {
        if (!message.isUser && !message.isLoading) {
            delay(500) // 等待500毫秒后显示按钮
            shouldShowButtons = true
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 当存在<think>内容且不是用户消息时，显示可折叠区域
                if (hasThinkContent) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isThinkExpanded = !isThinkExpanded }
                            .background(Color(0xFFE8F0FE), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "思考过程",
                                tint = Color(0xFF1A73E8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
    Text(
                                text = "思考过程",
                                color = Color(0xFF1A73E8),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (isThinkExpanded) 
                                          Icons.Default.KeyboardArrowUp 
                                       else 
                                          Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isThinkExpanded) "收起" else "展开",
                            tint = Color(0xFF1A73E8)
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = isThinkExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Text(
                            text = thinkContent,
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 8.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // 如果有普通内容，添加一个分隔线
                    if (normalContent.isNotEmpty()) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = textColor.copy(alpha = 0.2f)
                        )
                    }
                }
                
                // 显示非<think>标签的常规内容
                if (normalContent.isNotEmpty()) {
                    Text(
                        text = normalContent,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        // 操作按钮行 - 仅对AI回复显示且仅在回复后显示
        AnimatedVisibility(
            visible = !message.isUser && shouldShowButtons && !message.isLoading,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .align(alignment),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 复制按钮
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("回复内容", normalContent)
                        clipboard.setPrimaryClip(clip)
                        // 显示复制成功提示
                        scope.launch {
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_copy),
                        contentDescription = "复制",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // 点赞按钮
                IconButton(
                    onClick = { 
                        isLiked = !isLiked
                        if (isLiked) isDisliked = false
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_thumb_up),
                        contentDescription = "点赞",
                        tint = if (isLiked) Color(0xFF1A73E8) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // 倒赞按钮
                IconButton(
                    onClick = { 
                        isDisliked = !isDisliked
                        if (isDisliked) isLiked = false 
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_thumb_down),
                        contentDescription = "倒赞",
                        tint = if (isDisliked) Color.Red else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // 播放按钮
                IconButton(
                    onClick = { 
                        // TODO: 实现语音播放功能
                        Toast.makeText(context, "播放功能开发中", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play),
                        contentDescription = "播放",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // 分享按钮
                IconButton(
                    onClick = { 
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, normalContent)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "分享回复")
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_share),
                        contentDescription = "分享",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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
    var meetingSummary by remember { mutableStateOf("") }
    // 添加语音识别文本状态变量
    var transcriptText by remember { mutableStateOf("") }
    
    // 上传状态
    var isUploading by remember { mutableStateOf(false) }
    
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
            
            // 语音预览
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "语音预览",
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
                        // 使用可编辑的文本字段替代原来的MarkdownText
                        OutlinedTextField(
                            value = meetingSummary,
                            onValueChange = { meetingSummary = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp),
                            label = { Text("会议纪要") },
                            placeholder = { Text("请输入会议纪要内容...") },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2196F3),
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                        
                        // 显示语音识别文本（如果有）
                        if (audioRecords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 使用可折叠的语音识别文本
                            var isTranscriptExpanded by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isTranscriptExpanded = !isTranscriptExpanded }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "语音识别文本",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF616161)
                                )
                                
                                Icon(
                                    imageVector = if (isTranscriptExpanded) 
                                        Icons.Default.KeyboardArrowUp 
                                    else 
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isTranscriptExpanded) "收起" else "展开",
                                    tint = Color(0xFF757575)
                                )
                            }
                            
                            AnimatedVisibility(
                                visible = isTranscriptExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Divider(color = Color.LightGray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (transcriptText.isNotEmpty()) transcriptText else "服务器尚未返回语音识别文本",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Color(0xFFF5F5F5),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 上传按钮
                Button(
                    onClick = { 
                        // 显示上传中状态
                        isUploading = true
                        
                        // 使用协程处理文件上传和保存
                        scope.launch {
                            try {
                                // 如果有录音文件，上传到服务器获取会议纪要
                                if (audioRecords.isNotEmpty()) {
                                    // 显示文件存储路径
                                    val audioFile = audioRecords.first().file
                                    Toast.makeText(
                                        context,
                                        "音频文件路径：${audioFile.absolutePath}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    
                                    try {
                                        // 添加上传状态提示
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "正在上传文件，请稍候...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        
                                        val (summaryMarkdown, transcript, fileUrl) = uploadAudioAndGetSummary(audioFile, context)
                                        
                                        // 添加详细日志
                                        Log.d("MeetingRecord", "收到会议纪要长度: ${summaryMarkdown.length} 字符")
                                        Log.d("MeetingRecord", "收到会议纪要内容: $summaryMarkdown")
                                        Log.d("MeetingRecord", "收到转录文本: $transcript")
                                        Log.d("MeetingRecord", "收到文件URL: $fileUrl")
                                        
                                        if (summaryMarkdown.isNotEmpty()) {
                                            // 先显示成功提示
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "成功获取会议纪要，正在更新...",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            
                                            // 更新UI显示的会议纪要和转录文本（强制在主线程更新）
                                            withContext(Dispatchers.Main) {
                                                meetingSummary = summaryMarkdown
                                                
                                                // 保存转录文本到状态变量
                                                transcriptText = transcript ?: "未返回语音识别文本"
                                                
                                                // 强制刷新视图
                                                (context as? MainActivity)?.let { activity ->
                                                    activity.findViewById<androidx.compose.ui.platform.ComposeView>(android.R.id.content)?.let { view ->
                                                        view.invalidate()
                                                    }
                                                }
                                                
                                                // 使用延迟确保UI更新完成
                                                delay(500)
                                            }
                                            
                                            // 创建新的会议记录
                                            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                            val newRecord = MeetingRecord(
                                                title = "办事记录 $currentDate",
                                                date = currentDate,
                                                summary = summaryMarkdown,
                                                transcript = transcript,
                                                fileUrl = fileUrl,
                                                audioRecords = audioRecords,
                                                photos = photos
                                            )
                                            // 添加到列表开头
                                            AppState.meetingRecords.add(0, newRecord)
                                            // 保存到本地存储
                                            AppState.saveMeetingRecordsToLocal(context)
                                        } else {
                                            // 会议纪要为空
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "服务器返回的会议纪要为空",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                meetingSummary = "# 会议纪要\n\n服务器未能生成有效的会议纪要。"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MeetingRecord", "处理会议纪要失败", e)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "处理失败: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                                
                                // 显示成功提示
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "记录已保存到本地",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 显示错误提示
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "上传失败: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } finally {
                                // 无论成功失败，都重置上传状态
                                withContext(Dispatchers.Main) {
                                    isUploading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    enabled = !isUploading && (audioRecords.isNotEmpty() || photos.isNotEmpty() || meetingSummary.isNotEmpty())
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
                        text = if (isUploading) "处理中..." else "上传记录",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // 保存记录按钮
                Button(
                    onClick = { 
                        scope.launch {
                            try {
                                // 如果没有会议纪要但有录音或照片，也可以保存
                                val summary = if (meetingSummary.isNotEmpty()) {
                                    meetingSummary
                                } else {
                                    "# 会议纪要\n\n本次办事记录未生成会议纪要。"
                                }
                                
                                // 创建新的会议记录，包含语音识别文本
                                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                val newRecord = MeetingRecord(
                                    title = "办事记录 $currentDate",
                                    date = currentDate,
                                    summary = summary,
                                    transcript = if (transcriptText.isNotEmpty()) transcriptText else null,
                                    fileUrl = null,
                                    audioRecords = audioRecords,
                                    photos = photos
                                )
                                
                                // 添加到列表开头
                                AppState.meetingRecords.add(0, newRecord)
                                // 保存到本地存储
                                AppState.saveMeetingRecordsToLocal(context)
                                
                                // 显示成功提示
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "记录已保存到本地",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                
                                // 清空当前页面的数据
                                audioRecords = listOf()
                                photos = listOf()
                                meetingSummary = ""
                                transcriptText = ""
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 显示错误提示
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "保存失败: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50) // 绿色按钮，与上传按钮区分
                    ),
                    enabled = audioRecords.isNotEmpty() || photos.isNotEmpty() || meetingSummary.isNotEmpty()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_save),
                            contentDescription = "保存",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "保存记录",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen() {
    var selectedMeetingRecord by remember { mutableStateOf<MeetingRecord?>(null) }
    val meetingRecords = remember { AppState.meetingRecords }
    val scope = rememberCoroutineScope()
    
    // 添加对话框显示状态
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    
    // 反馈文本
    var feedbackText by remember { mutableStateOf("") }
    var isSendingFeedback by remember { mutableStateOf(false) }
    
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
                ProfileMenuItem(
                    title = "系统设置", 
                    subtitle = "通知、隐私与安全",
                    onClick = { showSettingsDialog = true }
                )
                Divider()
                ProfileMenuItem(
                    title = "帮助中心", 
                    subtitle = "常见问题与反馈",
                    onClick = { showFeedbackDialog = true }
                )
                Divider()
                ProfileMenuItem(
                    title = "关于", 
                    subtitle = "版本 1.0.0",
                    onClick = { showAboutDialog = true }
                )
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
                                var showDeleteDialog by remember { mutableStateOf(false) }
                                val context = LocalContext.current
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .combinedClickable(
                                            onClick = { selectedMeetingRecord = record },
                                            onLongClick = { showDeleteDialog = true }
                                        ),
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
                                
                                // 删除确认对话框
                                if (showDeleteDialog) {
                                    Dialog(onDismissRequest = { showDeleteDialog = false }) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp)
                                            ) {
                                                Text(
                                                    text = "删除办事记录",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "确定要删除该条办事记录吗？此操作不可恢复。",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(24.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    TextButton(onClick = { showDeleteDialog = false }) {
                                                        Text("取消")
                                                    }
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Button(
                                                        onClick = {
                                                            // 删除记录
                                                            AppState.meetingRecords.remove(record)
                                                            // 保存到本地
                                                            AppState.saveMeetingRecordsToLocal(context)
                                                            // 显示提示
                                                            Toast.makeText(context, "记录已删除", Toast.LENGTH_SHORT).show()
                                                            showDeleteDialog = false
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color.Red
                                                        )
                                                    ) {
                                                        Text("删除")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
                        .verticalScroll(rememberScrollState())
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
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 使用可编辑的文本字段替代原来的MarkdownText
                    var editableSummary by remember(selectedMeetingRecord) { 
                        mutableStateOf(selectedMeetingRecord?.summary ?: "") 
                    }
                    val context = LocalContext.current
                    
                    OutlinedTextField(
                        value = editableSummary,
                        onValueChange = { editableSummary = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp, max = 300.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2196F3),
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                    
                    // 显示语音识别文本（如果有）
                    selectedMeetingRecord!!.transcript?.let { transcript ->
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 使用可折叠的语音识别文本
                        var isTranscriptExpanded by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTranscriptExpanded = !isTranscriptExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "语音识别文本",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF616161)
                            )
                            
                            Icon(
                                imageVector = if (isTranscriptExpanded) 
                                    Icons.Default.KeyboardArrowUp 
                                else 
                                    Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isTranscriptExpanded) "收起" else "展开",
                                tint = Color(0xFF757575)
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = isTranscriptExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Divider(color = Color.LightGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = transcript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Color(0xFFF5F5F5),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                    
                    if (selectedMeetingRecord!!.photos.isNotEmpty()) {
                        Text(
                            text = "现场照片 (${selectedMeetingRecord!!.photos.size}张):",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 使用Row替代LazyRow，避免嵌套可滚动组件
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedMeetingRecord!!.photos.forEach { photo ->
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
                        Spacer(modifier = Modifier.height(8.dp))
                        
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { selectedMeetingRecord = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray
                            )
                        ) {
                            Text("关闭")
                        }
                        
                        Button(
                            onClick = { 
                                // 保存编辑后的会议纪要
                                selectedMeetingRecord?.let { record ->
                                    // 创建更新后的记录
                                    val updatedRecord = record.copy(summary = editableSummary)
                                    
                                    // 更新AppState中的记录
                                    val index = AppState.meetingRecords.indexOfFirst { it.id == record.id }
                                    if (index != -1) {
                                        AppState.meetingRecords[index] = updatedRecord
                                        // 保存到本地
                                        AppState.saveMeetingRecordsToLocal(context)
                                        Toast.makeText(context, "会议纪要已更新", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    selectedMeetingRecord = updatedRecord
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("保存更改")
                        }
                    }
                }
            }
        }
    }
    
    // 关于对话框
    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "关于",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "本应用中涉及到的大模型均为本地离线模型，请放心使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showAboutDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
    
    // 系统设置对话框
    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "系统设置",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "默认设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showSettingsDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
    
    // 帮助中心反馈对话框
    if (showFeedbackDialog) {
        val context = LocalContext.current
        
        Dialog(onDismissRequest = { showFeedbackDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "帮助中心",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        label = { Text("反馈") },
                        placeholder = { Text("反馈问题或建议") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2196F3),
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showFeedbackDialog = false }) {
                            Text("取消")
                        }
                        
                        Button(
                            onClick = {
                                if (feedbackText.isNotBlank()) {
                                    // 设置发送状态
                                    isSendingFeedback = true
                                    
                                    // 发送反馈到服务器
                                    scope.launch {
                                        try {
                                            // 调用发送反馈的函数
                                            val success = sendFeedback(feedbackText, context)
                                            
                                            withContext(Dispatchers.Main) {
                                                if (success) {
                                                    Toast.makeText(context, "反馈已发送，感谢您的建议！", Toast.LENGTH_SHORT).show()
                                                    feedbackText = "" // 清空反馈文本
                                                    showFeedbackDialog = false // 关闭对话框
                                                } else {
                                                    Toast.makeText(context, "发送失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                                }
                                                isSendingFeedback = false
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                isSendingFeedback = false
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "请输入反馈内容", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSendingFeedback && feedbackText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            if (isSendingFeedback) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = if (isSendingFeedback) "发送中..." else "发送")
                        }
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

// 上传录音文件到服务器并获取会议纪要
private suspend fun uploadAudioAndGetSummary(audioFile: File, context: Context): Triple<String, String?, String?> {
    return withContext(Dispatchers.IO) {
        try {
            // 检查网络连接
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            if (networkInfo == null || !networkInfo.isConnected) {
                throw Exception("网络连接不可用，请检查网络设置")
            }

            // 检查文件大小
            if (audioFile.length() > 200 * 1024 * 1024) { // 200MB
                throw Exception("音频文件大小超过限制（最大200MB）")
            }

            // 检查文件是否存在
            if (!audioFile.exists()) {
                throw Exception("音频文件不存在: ${audioFile.absolutePath}")
            }

            Log.d("UploadAudio", "开始上传文件: ${audioFile.absolutePath}")
            Log.d("UploadAudio", "文件大小: ${audioFile.length()} 字节")
            
            // 使用域名替代IP地址
            val url = URL("http://js2.blockelite.cn:14471/process_audio")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            
            // 设置连接和读取超时
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
            
            // 设置请求头
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            val outputStream = DataOutputStream(connection.outputStream)
            
            // 记录所有发送的数据
            val fileExtension = audioFile.extension.lowercase()
            Log.d("UploadAudio", "文件扩展名: $fileExtension")
            
            // 设置正确的Content-Type
            val contentType = when (fileExtension) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "ogg" -> "audio/ogg"
                else -> "audio/mpeg"
            }
            Log.d("UploadAudio", "使用Content-Type: $contentType")
            
            // 写入文件头部
            outputStream.writeBytes("--$boundary\r\n")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n")
            outputStream.writeBytes("Content-Type: $contentType\r\n")
            outputStream.writeBytes("\r\n")
            
            // 写入文件内容
            val fileInputStream = FileInputStream(audioFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
            }
            Log.d("UploadAudio", "已上传: $totalBytesRead / ${audioFile.length()} 字节")
            
            // 写入文件尾部
            outputStream.writeBytes("\r\n")
            outputStream.writeBytes("--$boundary--\r\n")
            
            fileInputStream.close()
            outputStream.flush()
            outputStream.close()
            
            // 获取响应
            val responseCode = connection.responseCode
            Log.d("UploadAudio", "服务器响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                inputStream.close()
                
                val responseText = response.toString()
                Log.d("UploadAudio", "服务器响应: $responseText")
                
                // 解析响应JSON
                val jsonResponse = JSONObject(responseText)
                
                // 记录完整的JSON结构
                Log.d("UploadAudio", "响应JSON结构: ${jsonResponse.toString(2)}")
                
                // 检查是否存在data对象，不依赖status字段
                val data = jsonResponse.optJSONObject("data")
                if (data != null) {
                    // 返回会议纪要、文本转录和文件URL
                    val summary = data.optString("summary", "")
                    val transcript = data.optString("transcript", null)
                    val fileUrl = data.optString("file_url", null)
                    
                    // 记录返回的实际数据
                    Log.d("UploadAudio", "解析到summary: $summary")
                    Log.d("UploadAudio", "解析到transcript: $transcript")
                    Log.d("UploadAudio", "解析到file_url: $fileUrl")
                    
                    // 确保至少有Markdown格式的摘要返回
                    if (summary.isBlank()) {
                        Log.e("UploadAudio", "返回的summary为空")
                        val defaultSummary = "# 会议纪要\n\n无法生成会议纪要，可能原因：\n- 录音质量不佳\n- 语音识别失败\n- 服务器处理错误"
                        return@withContext Triple(defaultSummary, transcript, fileUrl)
                    }
                    
                    return@withContext Triple(summary, transcript, fileUrl)
                } else if (jsonResponse.has("summary")) {
                    // 直接在根级别查找summary
                    val summary = jsonResponse.optString("summary", "")
                    val transcript = jsonResponse.optString("transcript", null)
                    val fileUrl = jsonResponse.optString("file_url", null)
                    
                    Log.d("UploadAudio", "从根级别解析到summary: $summary")
                    Log.d("UploadAudio", "从根级别解析到transcript: $transcript")
                    Log.d("UploadAudio", "从根级别解析到file_url: $fileUrl")
                    
                    if (summary.isBlank()) {
                        Log.e("UploadAudio", "根级别的summary为空")
                        val defaultSummary = "# 会议纪要\n\n无法生成会议纪要，可能原因：\n- 录音质量不佳\n- 语音识别失败\n- 服务器处理错误"
                        return@withContext Triple(defaultSummary, transcript, fileUrl)
                    }
                    
                    return@withContext Triple(summary, transcript, fileUrl)
                } else if (jsonResponse.has("error") || jsonResponse.has("message")) {
                    // 检查错误信息
                    val errorMessage = jsonResponse.optString("error", 
                                      jsonResponse.optString("message", "未知错误"))
                    throw Exception("服务器处理失败: $errorMessage")
                } else {
                    // 无法识别的响应格式
                    throw Exception("无法识别的响应格式: $responseText")
                }
            } else {
                // 读取错误响应
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    errorStream.close()
                    response.toString()
                } else {
                    "无错误详情"
                }
                
                Log.e("UploadAudio", "HTTP错误: $responseCode, 响应: $errorResponse")
                throw Exception("请求失败，HTTP状态码: $responseCode, 错误信息: $errorResponse")
            }
        } catch (e: Exception) {
            Log.e("UploadAudio", "上传异常", e)
            when (e) {
                is java.net.UnknownHostException -> {
                    throw Exception("无法连接到服务器，请检查网络连接")
                }
                is java.net.SocketTimeoutException -> {
                    throw Exception("连接超时，请检查网络连接")
                }
                else -> {
                    throw Exception("上传过程出错: ${e.message}")
                }
            }
        }
    }
}

// 修改会议纪要显示逻辑
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // 先记录输入的Markdown内容
        Log.d("MarkdownText", "收到Markdown内容 (${markdown.length}字符): ${markdown.take(100)}${if (markdown.length > 100) "..." else ""}")
        
        // 确保处理null或空字符串
        if (markdown.isNullOrBlank()) {
            Text(
                text = "暂无会议纪要内容",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            return@Column
        }
        
        // 规范化文本，处理各种换行符
        val normalizedText = markdown.replace("\r\n", "\n").replace("\r", "\n")
        
        // 按行分割
        val lines = normalizedText.split("\n")
        
        // 记录分割后的行数
        Log.d("MarkdownText", "分割为${lines.size}行")
        
        if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) {
            Text(
                text = "暂无会议纪要内容",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            return@Column
        }
        
        // 首先尝试检测是否是JSON格式
        if (lines.size == 1 && (lines[0].startsWith("{") || lines[0].startsWith("["))) {
            // 可能是JSON
            val jsonText = lines[0].trim()
            var isHandled = false
            
            if (jsonText.startsWith("{")) {
                // 在调用Composable前捕获异常
                val jsonSummary = parseJsonSummary(jsonText)
                if (jsonSummary != null) {
                    Log.d("MarkdownText", "从JSON中提取出summary: ${jsonSummary.take(50)}...")
                    // 递归调用以显示提取的summary
                    MarkdownText(jsonSummary, modifier)
                    isHandled = true
                }
            }
            
            // 如果没有找到有效内容，显示原始JSON
            if (!isHandled) {
                Text(
                    text = "服务器返回的JSON数据:\n$jsonText",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            return@Column
        }
        
        // 处理每一行
        lines.forEachIndexed { index, line ->
            // 记录每行的处理
            if (index < 10 || index >= lines.size - 10) { // 只记录前10行和后10行
                Log.d("MarkdownText", "处理第${index+1}/${lines.size}行: '${line.take(50)}${if (line.length > 50) "..." else ""}'")
            } else if (index == 10) {
                Log.d("MarkdownText", "...")
            }
            
            // 跳过空行，但保留一些垂直间距
            if (line.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                return@forEachIndexed
            }
            
            when {
                // 一级标题 (# 标题)
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                // 二级标题 (## 标题)
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                // 三级标题 (### 标题)
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                // 列表项 (- 项目)
                line.startsWith("- ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                        Text("•", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = line.removePrefix("- "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // 数字列表 (1. 项目)
                line.matches(Regex("^\\d+\\.\\s.*$")) -> {
                    val indexDot = line.indexOf(".")
                    if (indexDot > 0) {
                        Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                            Text(
                                text = line.substring(0, indexDot + 1),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = line.substring(indexDot + 1).trimStart(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                // 粗体 (**文本**)
                line.contains("**") -> {
                    // 预处理parts以避免composable内的try/catch
                    val parts = line.split("**")
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        for (i in parts.indices) {
                            if (i % 2 == 1) {
                                // 奇数索引的部分是加粗文本
                                Text(
                                    text = parts[i],
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                // 偶数索引的部分是普通文本
                                Text(parts[i])
                            }
                        }
                    }
                }
                // 普通文本
                else -> {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// 安全地解析JSON并提取summary，不会抛出异常
private fun parseJsonSummary(jsonText: String): String? {
    return try {
        val jsonObject = JSONObject(jsonText)
        if (jsonObject.has("summary")) {
            val summary = jsonObject.optString("summary", "")
            if (summary.isNotEmpty()) {
                return summary
            }
        }
        null
    } catch (e: Exception) {
        Log.e("MarkdownText", "JSON解析失败", e)
        null
    }
}

// 添加发送反馈函数
private suspend fun sendFeedback(content: String, context: Context): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // 检查网络连接
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            if (networkInfo == null || !networkInfo.isConnected) {
                throw Exception("网络连接不可用，请检查网络设置")
            }

            Log.d("Feedback", "开始发送反馈: ${content.take(50)}...")
            
            // 创建URL
            val url = URL("http://js2.blockelite.cn:14471/feedback")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            
            // 设置连接和读取超时
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // 设置请求头
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            
            // 创建JSON数据
            val jsonBody = JSONObject()
            jsonBody.put("content", content)
            
            // 写入请求体
            val outputStream = DataOutputStream(connection.outputStream)
            outputStream.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            
            // 获取响应
            val responseCode = connection.responseCode
            Log.d("Feedback", "服务器响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                inputStream.close()
                
                val responseText = response.toString()
                Log.d("Feedback", "服务器响应: $responseText")
                
                // 检查是否成功
                return@withContext true
            } else {
                // 读取错误响应
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    errorStream.close()
                    response.toString()
                } else {
                    "无错误详情"
                }
                
                Log.e("Feedback", "HTTP错误: $responseCode, 响应: $errorResponse")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("Feedback", "发送反馈异常", e)
            throw e
        }
    }
}