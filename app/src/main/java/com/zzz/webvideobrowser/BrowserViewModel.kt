package com.zzz.webvideobrowser

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BrowserViewModel : ViewModel() {
    enum class UiMode {
        HOME, WEB, RESOURCE_PANEL, FULLSCREEN_CUSTOM, FULLSCREEN_PSEUDO, SETTINGS
    }

    val uiMode = MutableLiveData(UiMode.HOME)
    val progress = MutableLiveData(0)
    val isLoading = MutableLiveData(false)
    val currentUrl = MutableLiveData("")
    
    // Video state
    val isPaused = MutableLiveData(true)
    val currentPosition = MutableLiveData(0.0)
    val currentDuration = MutableLiveData(0.0)

    fun setUiMode(mode: UiMode) {
        uiMode.value = mode
    }

    fun updateProgress(p: Int) {
        progress.value = p
        isLoading.value = p < 100
    }

    fun updateVideoState(pos: Double, dur: Double, paused: Boolean) {
        currentPosition.value = pos
        currentDuration.value = dur
        isPaused.value = paused
    }
}
