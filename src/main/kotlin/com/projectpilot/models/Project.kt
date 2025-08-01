package com.projectpilot.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProjectMember @JsonCreator constructor(
    @JsonProperty("email") val email: String,
    @JsonProperty("role") val role: String // e.g., "STUDENT", "PROFESSOR"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Project @JsonCreator constructor(
    @JsonProperty("_id") val _id: String,
    @JsonProperty("boardId") val boardId: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("createdBy") val createdBy: String, // creator email
    @JsonProperty("members") val members: List<ProjectMember> = listOf(),
    @JsonProperty("status") val status: String = "pending", // "pending", "in-progress", "completed"
    @JsonProperty("createdAt") val createdAt: Instant = Instant.now()
)
