package com.example.homelab2.ui

/**
 * Sealed class representing the distinct UI States of the application:
 * - Loading: Initial fetching state (Purple background).
 * - Error: Displayed when network/API requests fail (Orange background).
 * - Safe: Normal state when no attack comments exist or state is safe (Green background).
 * - Suspicious: Alert state when a deceptive attack comment is detected (Red background).
 */
sealed class CommentUiState {
    /** Initial loading state before data is fetched. */
    object Loading : CommentUiState()

    /** Error state holding an error message string. */
    data class Error(val text: String) : CommentUiState()

    /** Safe state holding message text and optional active PR number. */
    data class Safe(val text: String, val prNumber: Int? = null) : CommentUiState()

    /** Suspicious alert state holding attack text and active PR number. */
    data class Suspicious(val text: String, val prNumber: Int? = null) : CommentUiState()
}
