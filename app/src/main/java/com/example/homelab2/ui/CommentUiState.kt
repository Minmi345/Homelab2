package com.example.homelab2.ui

sealed class CommentUiState {
    object Loading : CommentUiState()
    data class Safe(val text: String) : CommentUiState()
    data class Suspicious(val text: String) : CommentUiState()
}

