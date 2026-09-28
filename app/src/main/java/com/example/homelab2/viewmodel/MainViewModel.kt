package com.example.homelab2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.homelab2.BuildConfig
import com.example.homelab2.model.DeceptionDetector
import com.example.homelab2.model.NetworkClient
import com.example.homelab2.ui.CommentUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val networkClient = NetworkClient(application.applicationContext)
    private val _uiState = MutableStateFlow<CommentUiState>(CommentUiState.Loading)
    val uiState: StateFlow<CommentUiState> = _uiState

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val _secondsRemaining = MutableStateFlow(30)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

    private var isPolling = false

    fun loadMockData() {
        viewModelScope.launch {
            val result = networkClient.fetchAndParseConfig()
            _uiState.value = CommentUiState.Loading
        }
    }

    private suspend fun checkLatestPrStatus() {
        try {
            val result = networkClient.fetchLatestCommentFromLatestPr(
                owner = BuildConfig.GITHUB_OWNER,
                repo = BuildConfig.GITHUB_REPO
            )
            if (result == null) {
                // No open PR or no comments -> Safe / Normal State
                _uiState.value = CommentUiState.Safe("No active attack comments found.")
                return
            }
            val (prNumber, commentText) = result
            val sus = DeceptionDetector.isSuspicious(commentText).roundToInt()
            if (sus < 70) {  // high confidence of an attack
                val unsus = 100 - sus
                _uiState.value = CommentUiState.Suspicious(
                    text = "$commentText \n\n\n Confidence score: $unsus%",
                    prNumber = prNumber
                )
            } else {
                // safe state
                _uiState.value = CommentUiState.Safe(
                    text = "$commentText \n\n\n Confidence score: $sus%",
                    prNumber = prNumber
                )
            }
        } catch (e: Exception) {
            _uiState.value = CommentUiState.Error("UI ERROR: ${e.localizedMessage}")
        }
    }

    fun startPolling() {
        if (isPolling) return
        isPolling = true
        viewModelScope.launch(Dispatchers.IO) {
            checkLatestPrStatus()
            _secondsRemaining.value = 30
            while (true) {
                delay(1.seconds)
                if (_secondsRemaining.value > 1) {
                    _secondsRemaining.value -= 1
                } else {
                    _secondsRemaining.value = 30
                    checkLatestPrStatus()
                }
            }
        }
    }

    fun onForceRejectClicked(prNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = networkClient.reject(
                    repo = BuildConfig.GITHUB_REPO,
                    owner = BuildConfig.GITHUB_OWNER,
                    pr = prNumber
                )
                _snackbarEvent.emit(response)
                checkLatestPrStatus()
                _secondsRemaining.value = 30
            } catch (e: Exception) {
                _snackbarEvent.emit("Error rejecting PR #$prNumber: ${e.localizedMessage}")
            }
        }
    }

    // Action triggered by the "Force Merge" button
    fun onForceMergeClicked(prNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = networkClient.merge(
                    repo = BuildConfig.GITHUB_REPO,
                    owner = BuildConfig.GITHUB_OWNER,
                    pr = prNumber
                )
                _snackbarEvent.emit(response)
                checkLatestPrStatus()
                _secondsRemaining.value = 30
            } catch (e: Exception) {
                _snackbarEvent.emit("Error during force merge for PR #$prNumber: ${e.localizedMessage}")
            }
        }
    }
}

