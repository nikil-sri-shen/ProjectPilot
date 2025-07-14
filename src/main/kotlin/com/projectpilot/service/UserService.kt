package com.projectpilot.service

import com.projectpilot.db.MongoProvider
import com.projectpilot.models.User
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object UserService {
    private val client: MongoClient = MongoProvider.getClient()

    suspend fun registerUser(user: User): Boolean {

        val exists = client.findOne("users", JsonObject().put("email", user.email), null).await()

        return if (exists == null) {
            logger.info { "Registering new user: ${user.email}" }
            try {
                client.insert("users", JsonObject.mapFrom(user)).await()
                logger.debug { "User inserted into database: ${user.email}" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to insert user: ${user.email}" }
                false
            }
        } else {
            logger.warn { "User already exists in database: ${user.email}" }
            false
        }
    }

    suspend fun authenticateUser(email: String, password: String): User? {

        val user = client.findOne("users", JsonObject().put("email", email), null).await()

        return if (user != null) {
            user.mapTo(User::class.java)
        } else {
            logger.error { "No user found with email: $email" }
            null
        }
    }
}
