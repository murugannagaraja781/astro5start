package com.astro5star.app.utils

import android.media.MediaPlayer
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl = _currentUrl.asStateFlow()

    fun play(url: String) {
        if (_currentUrl.value == url) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
            } else {
                mediaPlayer?.start()
                _isPlaying.value = true
            }
            return
        }

        stop()
        _currentUrl.value = url
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener {
                start()
                _isPlaying.value = true
            }
            setOnCompletionListener {
                _isPlaying.value = false
                _progress.value = 0f
            }
            prepareAsync()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentUrl.value = null
        _progress.value = 0f
    }

    suspend fun updateProgress() {
        while (true) {
            if (_isPlaying.value && mediaPlayer != null) {
                try {
                    val pos = mediaPlayer!!.currentPosition.toFloat()
                    val dur = mediaPlayer!!.duration.toFloat()
                    if (dur > 0) {
                        _progress.value = pos / dur
                    }
                } catch (e: Exception) {}
            }
            delay(100)
        }
    }
}
