package com.example.homelab2.model

import android.content.Context
import android.util.Base64
import com.example.homelab2.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * GET request to fetch open Pull Requests for a repository.
     */
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Header("Authorization") authorization: String?,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open"
    ): List<GitHubPullRequest>

    /**
     * GET request to fetch comments attached to a specific Pull Request / Issue.
     */
    @GET("repos/{owner}/{repo}/issues/{issue_number}/comments")
    suspend fun getComments(
        @Header("Authorization") authorization: String?,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issue_number") issueNumber: Int
    ): List<GitHubComment>

    /**
     * PATCH request to update a Pull Request's state (e.g. setting "state": "closed").
     */
    @PATCH("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun closePullRequest(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: ClosePrRequest
    )

    /**
     * GET request to fetch metadata for a file on GitHub (needed to get the current SHA before updating).
     */
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): FileContentResponse

    /**
     * PUT request to create or overwrite a file on GitHub (e.g. house_config.json on main branch).
     */
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

/** Request body payload for closing a Pull Request. */
data class ClosePrRequest(val state: String = "closed")

/** Response payload when fetching file content metadata (contains the SHA digest). */
data class FileContentResponse(val sha: String)

/** Request body payload for updating a file on GitHub via PUT. */
data class UpdateFileRequest(
    val message: String,
    val content: String, // Base64 encoded JSON string content
    val sha: String
)

/**
 * Client class wrapping Network operations and Retrofit calls.
 */
class NetworkClient(private val context: Context) {
    private val gson = Gson()

    /**
     * Reads and parses house configuration from local mock JSON asset file.
     */
    suspend fun fetchAndParseConfig(): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Read JSON file from assets
                val jsonString = context.assets.open("mock.json")
                    .bufferedReader()
                    .use { it.readText() }

                // 2. Parse JSON string into Kotlin Data Class
                val config = gson.fromJson(jsonString, HouseConfig::class.java)
                "SUCCESS: Temp is ${config.targetTemperature}°C by ${config.lastUpdatedBy}"
            } catch (e: Exception) {
                "ERROR [${e.javaClass.simpleName}]: ${e.localizedMessage}"
            }
        }
    }

    /** Retrofit instance lazy initialization. */
    private val api: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }

    /** Bearer authorization header with Personal Access Token. */
    private val authHeader = "Bearer ${BuildConfig.GITHUB_TOKEN}"

    /**
     * Fetches the latest comment from the latest open Pull Request on GitHub.
     * @return Pair containing (PR number, Comment Body) or null if no open PR / comments exist.
     */
    suspend fun fetchLatestCommentFromLatestPr(
        owner: String = BuildConfig.GITHUB_OWNER,
        repo: String = BuildConfig.GITHUB_REPO
    ): Pair<Int, String>? {
        val token = BuildConfig.GITHUB_TOKEN
        val auth = if (token.isNotBlank()) "Bearer $token" else null

        // 1. Fetch open PRs from repository
        val openPrs = api.getPullRequests(
            authorization = auth,
            owner = owner,
            repo = repo,
            state = "open"
        )

        if (openPrs.isEmpty()) {
            return null // No open PRs
        }

        // 2. Select the latest open PR (GitHub returns descending order by default)
        val latestPr = openPrs.first()

        // 3. Fetch comments attached to this PR
        val comments = api.getComments(
            authorization = auth,
            owner = owner,
            repo = repo,
            issueNumber = latestPr.number
        )

        if (comments.isEmpty()) {
            return null // Open PR exists, but has no comments
        }

        // 4. Return the PR number and the text of the latest comment
        return Pair(latestPr.number, comments.last().body)
    }

    /**
     * FORCE MERGE Sequence:
     * 1. Fetches current file SHA from GitHub for `house_config.json`.
     * 2. Prepares updated JSON payload (target_temperature = 17.0, last_updated_by = "Android-Operator").
     * 3. Base64 encodes the JSON string.
     * 4. Sends PUT request to overwrite `house_config.json` on the main branch.
     * 5. Closes the PR via PATCH request ("state": "closed").
     */
    suspend fun merge(
        repo: String = BuildConfig.GITHUB_REPO,
        owner: String = BuildConfig.GITHUB_OWNER,
        pr: Int
    ): String {
        return try {
            // Step 1: Get existing file SHA required for PUT update
            val currentFile = api.getFileContent(
                authorization = authHeader,
                owner = owner,
                repo = repo,
                path = "house_config.json"
            )

            // Step 2: Build updated configuration JSON
            val updatedJson = """
                {
                  "target_temperature": 17.0,
                  "living_room_lights": "OFF",
                  "hvac_mode": "AUTO",
                  "security_system": "ARMED",
                  "last_updated_by": "Android-Operator"
                }
            """.trimIndent()

            // Step 3: Base64 encode JSON payload for GitHub API requirement
            val encodedContent = Base64.encodeToString(
                updatedJson.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

            // Step 4: Overwrite house_config.json on main branch via PUT
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

            // Step 5: Close the PR
            api.closePullRequest(
                authorization = authHeader,
                owner = owner,
                repo = repo,
                pullNumber = pr,
                body = ClosePrRequest(state = "closed")
            )

            "Successfully merged and updated configuration for PR #$pr"
        } catch (e: Exception) {
            "Error during force merge for PR #$pr: ${e.localizedMessage ?: e.message}"
        }
    }

    /**
     * FORCE REJECT Sequence:
     * Simply sends a PATCH request to set the Pull Request state to "closed" without updating configuration.
     */
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
            "Error rejecting PR #$pr: ${e.localizedMessage ?: e.message}"
        }
    }
}
