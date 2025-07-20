package com.projectpilot.utils

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

val mapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule()) // ✅ Support for java.time.Instant, LocalDate, etc.
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // ✅ Use ISO 8601 format
}
