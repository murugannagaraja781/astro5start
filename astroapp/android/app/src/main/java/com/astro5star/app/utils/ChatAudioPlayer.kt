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

        try {
            stop()
            _currentUrl.value = url
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    start()
                    _isPlaying.value = true
                    android.util.Log.d("ChatAudioPlayer", "Playback started: $url")
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progress.value = 0f
                    _currentUrl.value = null
                    android.util.Log.d("ChatAudioPlayer", "Playback completed")
                }
                setOnErrorListener { mp, what, extra ->
                    android.util.Log.e("ChatAudioPlayer", "Playback ERROR: what=$what, extra=$extra URL=$url")
                    _isPlaying.value = false
                    _currentUrl.value = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatAudioPlayer", "Play failed: ${e.message}", e)
            _isPlaying.value = false
            _currentUrl.value = null
        }
    }
    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            _isPlaying.value = false
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
            val player = mediaPlayer
            if (_isPlaying.value && player != null) {
                try {
                    val pos = player.currentPosition.toFloat()
                    val dur = player.duration.toFloat()
                    if (dur > 0) {
                        _progress.value = pos / dur
                    }
                } catch (e: Exception) {
                    // Log potentially but don't crash
                }
            }
            delay(100)
        }
    }
}
