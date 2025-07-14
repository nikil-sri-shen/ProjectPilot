package com.projectpilot.models

import java.time.Instant
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class User @JsonCreator constructor(
    @JsonProperty("username")val username : String,
    @JsonProperty("email") val email : String,
    @JsonProperty("password") val password : String,
    @JsonProperty("createdAt")val createAt : Instant? = Instant.now()
)

data class SafeUser(
    val email: String,
    val username: String,
)
