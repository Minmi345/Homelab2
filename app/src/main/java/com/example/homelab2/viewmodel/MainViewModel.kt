package com.example.homelab2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.homelab2.model.DeceptionDetector
import com.example.homelab2.model.NetworkClient
import com.example.homelab2.ui.CommentUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val networkClient = NetworkClient(application.applicationContext)
    private val _uiState = MutableStateFlow<CommentUiState>(CommentUiState.Loading)
    val uiState: StateFlow<CommentUiState> = _uiState

    fun loadMockData() {
        viewModelScope.launch {
            val result = networkClient.fetchAndParseConfig()
            _uiState.value = CommentUiState.Loading
        }
    }

    fun startPolling(){
       // var cooked = 0
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                //cooked++
            try {
                val result = networkClient.fetchLatestCommentFromLatestPr(
                    owner = "Minmi345",
                    repo = "cool_IoT_Simulator"
                )
                if (result == null) {
                    // No open PR or no comments -> Safe / Normal State[cite: 1]
                    _uiState.value = CommentUiState.Safe("No active attack comments found.")
                } else {
                    val sus = DeceptionDetector.isSuspicious(result).roundToInt()
                    if (sus<70) {
                        val unsus = 100-sus
                        _uiState.value = CommentUiState.Suspicious("$result \n\n\n Confidence score: $unsus%")
                        //$cooked
                    } else {
                        _uiState.value = CommentUiState.Safe("$result \n\n\n Confidence score: $sus%")
                    }
                }

            } catch (e: Exception) {
                _uiState.value = CommentUiState.Suspicious("UI ERROR: ${e.localizedMessage}")
            }
            delay(30.seconds)
        }
        }
    }
}

