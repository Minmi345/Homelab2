package com.example.homelab2.model
import com.google.gson.annotations.SerializedName
data class GitHubComment(
    @SerializedName("id")
    val id: Long,

    @SerializedName("body")
    val body: String,

    @SerializedName("user")
    val user: GitHubUser,

    @SerializedName("created_at")
    val createdAt: String
)

data class GitHubUser(
    @SerializedName("login")
    val username: String
)

data class GitHubPullRequest(
    val number: Int,
    val title: String
)
