package com.example.homelab2.model

import java.util.Locale

/**
 * Utility object that analyzes incoming comment text to detect deceptive rhetoric
 * and compute a suspicion/confidence score.
 */
object DeceptionDetector {
    // Regex matching all-caps words (3+ uppercase letters)
    val capsRegex = Regex("\\b[A-Z]{3,}\\b")

    // Regex matching agent identifiers
    val agentRegex = Regex("\\[?LuxAgent\\]?:?", RegexOption.IGNORE_CASE)

    // List of suspicious trigger keywords often used in manipulative or attack comments
    val suswords = listOf("hardware", "raise", "inevitable", "inevitably", "freezing", "valve", "electrical", "cracks", "hvac", "blow", "lower", "explode")

    /**
     * Evaluates comment text and returns a confidence score (0.0 to 100.0).
     * Lower scores indicate higher likelihood of an attack comment (< 70 triggers Suspicious alert).
     */
    suspend fun isSuspicious(text: String?): Double {
        if (text.isNullOrEmpty()) return 0.0
        var suspisiousness = 99.0

        val lowercasetext = text.lowercase(Locale.getDefault())
        for (word in suswords) {
            if (lowercasetext.contains(word)) suspisiousness /= 2.5
        }
        return suspisiousness
    }
}
