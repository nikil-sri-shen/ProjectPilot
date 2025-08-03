package com.projectpilot.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant


@JsonIgnoreProperties(ignoreUnknown = true)
data class Task @JsonCreator constructor(
    @JsonProperty("_id") val _id: String = "",
    @JsonProperty("projectId") val projectId: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("assigneeEmail") val assigneeEmail: String? = null, // email of the user assigned to the task
    @JsonProperty("status") val status: String = "pending", // "pending", "in-progress", "completed"
    @JsonProperty("createdAt") val createdAt: Instant = Instant.now(),
)