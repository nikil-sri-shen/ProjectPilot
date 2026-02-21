package com.projectpilot.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class BoardMember @JsonCreator constructor(
    @JsonProperty("email") val email: String,
    @JsonProperty("role") val role: String // ORGANIZER | MENTOR | JUDGE | PARTICIPANT
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Board @JsonCreator constructor(
    @JsonProperty("_id") var _id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("createdBy") val createdBy: String,
    @JsonProperty("members") val members: List<BoardMember> = listOf(),
    @JsonProperty("projects") val projects: List<String> = listOf(),
    @JsonProperty("createdAt") val createdAt: Instant = Instant.now()
)
