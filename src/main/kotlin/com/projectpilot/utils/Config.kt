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
}