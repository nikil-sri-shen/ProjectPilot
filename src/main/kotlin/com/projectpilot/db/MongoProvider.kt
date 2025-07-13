package com.projectpilot.db

import com.projectpilot.utils.Config
import io.vertx.core.Vertx
import io.vertx.ext.mongo.MongoClient

private val logger = mu.KotlinLogging.logger {}

object MongoProvider {
    private lateinit var client : MongoClient

    fun init(vertx: Vertx) {
        if (!::client.isInitialized) {
            try {
                val mongoConfig = io.vertx.core.json.JsonObject()
                    .put("connection_string", Config.mongoUri)
                    .put("db_name", Config.mongoDbName)

                client = MongoClient.createShared(vertx, mongoConfig)
                logger.info("MongoDB client initialized with URI: ${Config.mongoUri}")
            } catch (e: Exception) {
                logger.info("Failed to initialize MongoDB client: ${e.message}")
                e.printStackTrace()
            }
        } else {
            logger.info("MongoDB client is already initialized.")
        }
    }

    fun getClient(): MongoClient {
        if (!::client.isInitialized) {
            throw IllegalStateException("MongoDB client is not initialized. Call init() first.")
        }
        return client
    }
}