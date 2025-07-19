package com.projectpilot.utils

import java.util.Properties

object Config {

    private val properties: Properties = Properties().apply {
        val stream = this@Config.javaClass.classLoader.getResourceAsStream("config.properties")
            ?: throw IllegalStateException("config.properties file not found in resources folder")
        load(stream)
    }

    val server_port: Int get() = properties.getProperty("SERVER.PORT").toInt()
    val mongoUri: String get() = properties.getProperty("MONGO.URI")
    val mongoDbName: String get() = properties.getProperty("MONGO.DB_NAME")
    val jwtSecret: String get() = properties.getProperty("JWT.SECRET")
    val fromEmail: String get() = properties.getProperty("FROM.EMAIL")
    val appPassword: String get() = properties.getProperty("APP.PASSWORD")
    val smtpHost : String get() = properties.getProperty("SMTP.HOST", "smtp.gmail.com")
    val smtpPort : String get() = properties.getProperty("SMTP.PORT", "587")
}