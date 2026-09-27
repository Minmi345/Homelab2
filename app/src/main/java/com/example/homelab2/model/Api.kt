package com.example.homelab2.model

import kotlinx.coroutines.Dispatchers
import android.content.Context
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.example.homelab2.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Header("Authorization") authorization: String?,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open"
    ): List<GitHubPullRequest>
    @GET("repos/{owner}/{repo}/issues/{issue_number}/comments")
    suspend fun getComments(
        @Header("Authorization") authorization: String?,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issue_number") issueNumber: Int
    ): List<GitHubComment>
}
class NetworkClient (private val context: Context) {
    private val gson = Gson()

    suspend fun fetchData(): String {
        return withContext(Dispatchers.IO) {
            // Simulated network fetch
            "Data fetched successfully!"
        }
    }

    suspend fun fetchAndParseConfig(): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Read JSON file from assets
                val jsonString = context.assets.open("mock.json")
                    .bufferedReader()
                    .use { it.readText() }

                // 2. Parse JSON string into Kotlin Data Class
                val config = gson.fromJson(jsonString, HouseConfig::class.java)
//                config
                "SUCCESS: Temp is ${config.targetTemperature}°C by ${config.lastUpdatedBy}"
            } catch (e: Exception) {
                "ERROR [${e.javaClass.simpleName}]: ${e.localizedMessage}"
            }
        }
    }

    private val api: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }

    suspend fun fetchLatestCommentFromLatestPr(owner: String, repo: String): String? {
        val token = BuildConfig.GITHUB_TOKEN
        val authHeader = if (token.isNotBlank()) "Bearer $token" else null

        // 1. Fetch open PRs
        val openPrs = api.getPullRequests(
            authorization = authHeader,
            owner = owner,
            repo = repo,
            state = "open"
        )

        if (openPrs.isEmpty()) {
            return null // No open PRs
        }

        // GitHub returns PRs in descending order of creation by default
        val latestPr = openPrs.first()

        // 2. Fetch comments for the latest open PR
        val comments = api.getComments(
            authorization = authHeader,
            owner = owner,
            repo = repo,
            issueNumber = latestPr.number
        )

        if (comments.isEmpty()) {
            return null // Open PR exists, but has no comments
        }

        // 3. Return the body of the last comment
        return comments.last().body
    }


}