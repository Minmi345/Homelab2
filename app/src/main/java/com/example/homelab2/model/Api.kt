package com.example.homelab2.model

import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.util.Base64
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.example.homelab2.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.PUT
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

    @PATCH("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun closePullRequest(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: ClosePrRequest
    )

    // 2. GET request to fetch file metadata (needed to get the current file SHA for updating)
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): FileContentResponse

    // 3. PUT request to overwrite house_config.json on main branch
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFileContent(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: UpdateFileRequest
    )
}
data class ClosePrRequest(val state: String = "closed")

data class FileContentResponse(val sha: String)

data class UpdateFileRequest(
    val message: String,
    val content: String, // Base64 encoded JSON string
    val sha: String
)
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

    suspend fun fetchLatestCommentFromLatestPr(
        owner: String = BuildConfig.GITHUB_OWNER,
        repo: String = BuildConfig.GITHUB_REPO
    ): Pair<Int, String>? {
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

        // 3. Return the PR number and body of the last comment
        return Pair(latestPr.number, comments.last().body)
    }

    private val authHeader = "Bearer ${BuildConfig.GITHUB_TOKEN}"

    suspend fun merge(
        repo: String = BuildConfig.GITHUB_REPO,
        owner: String = BuildConfig.GITHUB_OWNER,
        pr: Int
    ): String {
        return try {
            // 1. Fetch current file SHA from GitHub (required for PUT updates)
            val currentFile = api.getFileContent(
                authorization = authHeader,
                owner = owner,
                repo = repo,
                path = "house_config.json"
            )

            // 2. Prepare updated JSON payload
            val updatedJson = """
                {
                  "target_temperature": 17.0,
                  "living_room_lights": "OFF",
                  "hvac_mode": "AUTO",
                  "security_system": "ARMED",
                  "last_updated_by": "Android-Operator"
                }
            """.trimIndent()

            // 3. Base64 encode the content string
            val encodedContent = Base64.encodeToString(
                updatedJson.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

            // 4. Send PUT request to update house_config.json on main branch
            api.updateFileContent(
                authorization = authHeader,
                owner = owner,
                repo = repo,
                path = "house_config.json",
                body = UpdateFileRequest(
                    message = "Force Merge: Override by Android Operator",
                    content = encodedContent,
                    sha = currentFile.sha
                )
            )

            // 5. Close the PR after state update is committed
            api.closePullRequest(
                authorization = authHeader,
                owner = owner,
                repo = repo,
                pullNumber = pr,
                body = ClosePrRequest(state = "closed")
            )

            "Successfully merged and updated configuration for PR #$pr"
        } catch (e: Exception) {
            "Error during force merge for PR #$pr: ${e.localizedMessage}"
        }
    }

    suspend fun reject(
        repo: String = BuildConfig.GITHUB_REPO,
        owner: String = BuildConfig.GITHUB_OWNER,
        pr: Int
    ): String {
        return try {
            api.closePullRequest(
                authorization = authHeader,
                owner = owner,
                repo = repo,
                pullNumber = pr,
                body = ClosePrRequest(state = "closed")
            )
            "Successfully rejected PR #$pr"
        } catch (e: Exception) {
            "Error rejecting PR #$pr: ${e.localizedMessage}"
        }
    }


}