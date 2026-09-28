package com.example.homelab2.ui

sealed class CommentUiState {
    object Loading : CommentUiState()
    data class Error(val text: String) : CommentUiState()
    data class Safe(val text: String, val prNumber: Int? = null) : CommentUiState()
    data class Suspicious(val text: String, val prNumber: Int? = null) : CommentUiState()
}

