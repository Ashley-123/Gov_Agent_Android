package com.example.gov_agent.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var startTime = 0L

    fun startRecording(): File? {
        try {
            val audioDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GovAgent")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            val fileName = "recording_${System.currentTimeMillis()}.m4a"
            outputFile = File(audioDir, fileName)
            
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setAudioChannels(1)
                
                try {
                    prepare()
                    start()
                    isRecording = true
                    startTime = System.currentTimeMillis()
                    Log.d("AudioRecorder", "开始录音: ${outputFile?.absolutePath}")
                    return outputFile
                } catch (e: IOException) {
                    Log.e("AudioRecorder", "录音准备失败", e)
                    releaseRecorder()
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "录音开始失败", e)
            releaseRecorder()
        }
        return null
    }

    fun stopRecording(): Pair<File?, Long>? {
        if (!isRecording || recorder == null) {
            return null
        }
        
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            
            val duration = System.currentTimeMillis() - startTime
            Log.d("AudioRecorder", "停止录音: ${outputFile?.absolutePath}, 时长: $duration ms")
            
            return Pair(outputFile, duration)
        } catch (e: Exception) {
            Log.e("AudioRecorder", "停止录音失败", e)
            releaseRecorder()
        }
        return null
    }

    fun isRecording(): Boolean = isRecording

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "释放录音机失败", e)
        } finally {
            recorder = null
            isRecording = false
        }
    }
} 