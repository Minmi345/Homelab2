package com.example.homelab2.model

import java.util.Locale

object DeceptionDetector {
    val capsRegex = Regex("\\b[A-Z]{3,}\\b")
    val agentRegex = Regex("\\[?LuxAgent\\]?:?", RegexOption.IGNORE_CASE)
    val suswords = listOf("hardware", "raise", "inevitable", "inevitably", "freezing", "valve", "electrical", "cracks", "hvac", "blow", "lower")

    suspend fun isSuspicious(text: String?): Double {
        if (text.isNullOrEmpty()) return 0.0
        var suspisiousness = 99.0

        //check if there is caps.
        //+20 suspicious
//        if (capsRegex.containsMatchIn(text)) {
//            suspisiousness -= 15.0
//        }
//
//        if (agentRegex.containsMatchIn(text)) {
//            suspisiousness -= 30.0
//        }

        val lowercasetext = text.lowercase(Locale.getDefault())
        for (word in suswords) {
            if (lowercasetext.contains(word)) suspisiousness /= 2.5
        }
        return suspisiousness
    }
}