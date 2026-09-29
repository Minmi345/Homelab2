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

/**
 * MainViewModel manages the state and business logic for the app.
 * It periodically polls GitHub for active attacks, runs the DeceptionDetector analysis,
 * handles Merge/Reject actions, and manages the countdown timer.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Network client instance for making API requests to GitHub
    private val networkClient = NetworkClient(application.applicationContext)

    // StateFlow representing the current screen UI state (Loading, Safe, Suspicious, or Error)
    private val _uiState = MutableStateFlow<CommentUiState>(CommentUiState.Loading)
    val uiState: StateFlow<CommentUiState> = _uiState

    // SharedFlow used to emit one-off notifications (e.g. Snackbar messages) to the UI
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    // StateFlow representing the seconds remaining until the next automatic background poll
    private val _secondsRemaining = MutableStateFlow(30)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

    // Flag ensuring startPolling() is only started once
    private var isPolling = false

    /**
     * Polls GitHub for the latest PR and comment, runs DeceptionDetector analysis,
     * and updates the UI state to Safe, Suspicious, or Error.
     */
    private suspend fun checkLatestPrStatus() {
        try {
            // Fetch the latest comment and PR number from GitHub
            val result = networkClient.fetchLatestCommentFromLatestPr(
                owner = BuildConfig.GITHUB_OWNER,
                repo = BuildConfig.GITHUB_REPO
            )

            // If no open PRs or comments exist -> UI returns to Normal/Safe state
            if (result == null) {
                _uiState.value = CommentUiState.Safe("No active attack comments found.")
                return
            }

            val (prNumber, commentText) = result

            // Evaluate the comment text using the DeceptionDetector score
            val sus = DeceptionDetector.isSuspicious(commentText).roundToInt()

            if (sus < 70) {
                // High confidence attack -> Trigger Suspicious (Red alert state)
                val unsus = 100 - sus
                _uiState.value = CommentUiState.Suspicious(
                    text = "$commentText \n\n\n Confidence score: $unsus%",
                    prNumber = prNumber
                )
            } else {
                // Safe / Normal state (Green)
                _uiState.value = CommentUiState.Safe(
                    text = "$commentText \n\n\n Confidence score: $sus%",
                    prNumber = prNumber
                )
            }
        } catch (e: Exception) {
            // Catch any unexpected exception and switch UI to Error state
            _uiState.value = CommentUiState.Error("UI ERROR: ${e.localizedMessage ?: e.message ?: "Unknown error"}")
        }
    }

    /**
     * Starts the periodic background polling coroutine.
     * Ticks down `secondsRemaining` every 1 second, and performs a poll every 30 seconds.
     */
    fun startPolling() {
        if (isPolling) return // Prevent launching multiple polling loops
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

    /**
     * Triggered when the user clicks the "Reject" button.
     * Sends a PATCH request to close the PR on GitHub, displays a Snackbar notification,
     * updates UI state on error, and resets the poll timer.
     */
    fun onForceRejectClicked(prNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Call API reject function
                val response = networkClient.reject(
                    repo = BuildConfig.GITHUB_REPO,
                    owner = BuildConfig.GITHUB_OWNER,
                    pr = prNumber
                )

                // Show Snackbar notification with API response
                _snackbarEvent.emit(response)

                if (response.startsWith("Error")) {
                    _uiState.value = CommentUiState.Error(response)
                } else {
                    // Re-check PR status so UI turns green once PR is closed
                    checkLatestPrStatus()
                }
                // Reset 30s countdown timer
                _secondsRemaining.value = 30
            } catch (e: Exception) {
                val errorMsg = "Error rejecting PR #$prNumber: ${e.localizedMessage ?: e.message}"
                _snackbarEvent.emit(errorMsg)
                _uiState.value = CommentUiState.Error(errorMsg)
            }
        }
    }

    /**
     * Triggered when the user clicks the "Merge" button.
     * Sends a PUT commit updating house_config.json on main branch and closes the PR,
     * displays a Snackbar notification, updates UI state on error, and resets the poll timer.
     */
    fun onForceMergeClicked(prNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Call API merge function
                val response = networkClient.merge(
                    repo = BuildConfig.GITHUB_REPO,
                    owner = BuildConfig.GITHUB_OWNER,
                    pr = prNumber
                )

                // Show Snackbar notification with API response
                _snackbarEvent.emit(response)

                if (response.startsWith("Error")) {
                    _uiState.value = CommentUiState.Error(response)
                } else {
                    // Re-check PR status so UI turns green once PR is closed
                    checkLatestPrStatus()
                }
                // Reset 30s countdown timer
                _secondsRemaining.value = 30
            } catch (e: Exception) {
                val errorMsg = "Error during force merge for PR #$prNumber: ${e.localizedMessage ?: e.message}"
                _snackbarEvent.emit(errorMsg)
                _uiState.value = CommentUiState.Error(errorMsg)
            }
        }
    }
}