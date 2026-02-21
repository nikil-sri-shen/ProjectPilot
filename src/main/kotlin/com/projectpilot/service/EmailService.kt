package com.projectpilot.service

import com.projectpilot.db.MongoProvider
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import java.time.Instant
import java.util.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

object EmailService {
    private val client: MongoClient = MongoProvider.getClient()

    suspend fun createVerificationToken(email: String): String? {
        val token = UUID.randomUUID().toString().take(6).uppercase() // 6-char code
        val expirationTime = Instant.now().plusSeconds(600) // Token valid for 10 minutes

        val query = JsonObject().put("email", email)
        val update = JsonObject()
            .put("email", email)
            .put("token", token)
            .put("expiresAt", expirationTime.toString())

        return try {
            val existing = client.findOne("email_verifications", query, null).await()

            if (existing != null) {
                // Update existing token
                client.replaceDocuments("email_verifications", query, update).await()
                logger.info { "Updated verification token for email: $email" }
            } else {
                // Insert new token
                client.save("email_verifications", update).await()
                logger.info { "Created new verification token for email: $email" }
            }

            token
        } catch (e: Exception) {
            logger.error(e) { "Failed to create/update verification token for email: $email" }
            null
        }
    }

    suspend fun verifyToken(email: String, token: String): Boolean {
        val query = JsonObject().put("email", email).put("token", token)
        val result = client.findOne("email_verifications", query, null).await()

        if (result != null) {
            val expiresAt = Instant.parse(result.getString("expiresAt"))
            if (Instant.now().isBefore(expiresAt)) {
                logger.info { "Verification token verified for email: $email" }
                client.removeDocument("email_verifications", query).await() // Remove token after verification
                return true
            } else {
                logger.warn { "Verification token expired for email: $email" }
            }
        } else {
            logger.warn { "No verification token found for email: $email" }
        }
        return false
    }
}
