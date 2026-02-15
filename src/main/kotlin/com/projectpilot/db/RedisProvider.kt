package com.projectpilot.db

import io.vertx.core.Vertx
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI

object RedisProvider {

    private lateinit var redisAPI: RedisAPI
    private var initialized = false

    /**
     * Initialize Redis connection
     */
    fun init(vertx: Vertx) {
        if (!initialized) {
            val redis = Redis.createClient(vertx, "redis://localhost:6379")
            redisAPI = RedisAPI.api(redis)
            initialized = true
            println("✅ Redis connected on localhost:6379")
        }
    }

    /**
     * Get RedisAPI instance
     */
    fun client(): RedisAPI {
        if (!initialized) {
            throw IllegalStateException("RedisProvider not initialized! Call RedisProvider.init(vertx) first.")
        }
        return redisAPI
    }

    fun close() {
        if (initialized) {
            client().close()
            println("Redis connection closed.")
        }
    }
}
