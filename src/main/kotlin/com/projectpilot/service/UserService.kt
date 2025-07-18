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

    suspend fun getUserByEmail(email: String): User? {

        val user = client.findOne("users", JsonObject().put("email", email), null).await()

        return if (user != null) {
            user.mapTo(User::class.java)
        } else {
            logger.error { "No user found with email: $email" }
            null
        }
    }

    suspend fun updateUser(email: String, updatedFields: Map<String, String>?) {
        if (updatedFields == null || updatedFields.isEmpty()) {
            logger.warn { "No fields provided for update for user: $email" }
            return
        }
        val existingUser = client.findOne("users", JsonObject().put("email", email), null).await()
        if (existingUser == null) {
            logger.error { "User not found: $email" }
            return
        }
        val updateObject = JsonObject().apply {
            updatedFields.forEach { (key, value) -> put(key, value) }
        }
        try {
            val result = client.updateCollection(
                "users",
                JsonObject().put("email", email),
                JsonObject().put("\$set", updateObject)
            ).await()

            if (result.docModified.toInt() == 1) {
                logger.info { "User updated successfully: $email" }
            } else {
                logger.warn { "User update had no effect: $email" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating user: $email" }
        }
    }

    suspend fun deleteUserByEmail(email: String): Boolean {
        val existingUser = client.findOne("users", JsonObject().put("email", email), null).await()
        if (existingUser == null) {
            logger.error { "User not found: $email" }
            false
        }
        val query = JsonObject().put("email", email)
        return try {
            val result = client.removeDocuments("users", query).await()
            result.removedCount.toInt() == 1
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete user: $email" }
            false
        }
    }
}
