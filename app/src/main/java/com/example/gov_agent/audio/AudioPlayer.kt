package com.example.gov_agent.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var playerIsPlaying = false
    private var currentPlayingFile: File? = null
    private var onCompletionListener: (() -> Unit)? = null
    
    fun playFile(file: File, onCompletion: () -> Unit = {}) {
        if (playerIsPlaying) {
            stopPlaying()
        }
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                setOnCompletionListener {
                    playerIsPlaying = false
                    onCompletionListener?.invoke()
                    release()
                    mediaPlayer = null
                }
                prepare()
                start()
                playerIsPlaying = true
                currentPlayingFile = file
                onCompletionListener = onCompletion
                Log.d("AudioPlayer", "开始播放: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "播放失败", e)
            releasePlayer()
        }
    }
    
    fun stopPlaying() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
                Log.d("AudioPlayer", "停止播放")
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "停止播放失败", e)
        } finally {
            mediaPlayer = null
            playerIsPlaying = false
            currentPlayingFile = null
            onCompletionListener = null
        }
    }
    
    fun isPlaying(): Boolean = playerIsPlaying
    
    fun getCurrentPlayingFile(): File? = currentPlayingFile
    
    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "释放播放器失败", e)
        } finally {
            mediaPlayer = null
            playerIsPlaying = false
            currentPlayingFile = null
        }
    }
} 