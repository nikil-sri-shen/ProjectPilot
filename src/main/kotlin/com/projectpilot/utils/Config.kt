package com.projectpilot.utils

import java.util.Properties

object Config {

    private val properties: Properties = Properties().apply {
        val stream = this@Config.javaClass.classLoader
            .getResourceAsStream("config.properties")
        stream?.let { load(it) }   // Only load if file exists
    }

    private fun get(key: String, default: String? = null): String {
        return System.getenv(key)
            ?: properties.getProperty(key)
            ?: default
            ?: throw IllegalStateException("Missing required config key: $key")
    }

    val serverPort: Int
        get() = get("SERVER_PORT", "9090").toInt()

    val mongoUri: String
        get() = get("MONGO_URI")

    val mongoDbName: String
        get() = get("MONGO_DB_NAME")

    val jwtSecret: String
        get() = get("JWT_SECRET")

    val fromEmail: String
        get() = get("FROM_EMAIL")

    val appPassword: String
        get() = get("APP_PASSWORD")

    val smtpHost: String
        get() = get("SMTP_HOST", "smtp.gmail.com")

    val smtpPort: String
        get() = get("SMTP_PORT", "587")
}