package com.projectpilot.routes

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectpilot.models.User
import com.projectpilot.service.UserService
import com.projectpilot.utils.JwtConfig
import com.projectpilot.utils.mapper
import io.vertx.core.Vertx
import io.vertx.core.http.Cookie
import at.favre.lib.crypto.bcrypt.BCrypt
import com.projectpilot.middleware.jwtMiddleware
import com.projectpilot.models.SafeUser
import com.projectpilot.service.EmailVerificationService
import com.projectpilot.utils.Mailer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

fun authRoutes(vertx: Vertx, router: Router) {
    val authRouter = Router.router(vertx)

    authRouter.post("/signup").handler(::handleSignup)
    authRouter.post("/verify-email").handler(::handleEmailVerification)
    authRouter.post("/signin").handler(::handleSignin)
    authRouter.post("/logout").handler(::handleLogout)
    authRouter.post("/request-password-reset").handler(::handleRequestPasswordReset)
    authRouter.post("/reset-password").handler(::handleResetPassword)
    // ✅ Protected route using JWT middleware
    authRouter.get("/profile").handler(jwtMiddleware()).handler(::handleProfile)
    authRouter.put("/profile").handler(jwtMiddleware()).handler(::handleUpdateProfile)
    authRouter.delete("/profile").handler(jwtMiddleware()).handler(::handleDeleteProfile).handler(::handleLogout)
    router.mountSubRouter("/auth", authRouter)
    logger.info { "Auth routes mounted under /auth" }
}

fun handleSignup(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val rawBody = ctx.body().asString()
            val user = mapper.readValue<User>(rawBody)

            // Basic validation
            if (!user.email.contains("@") || user.password.length < 6) {
                logger.warn { "Invalid email or password too short: email=${user.email}" }
                ctx.response().setStatusCode(400).end("Invalid email or password")
                return@launch
            }

            // Check if user already exists
            val exists = UserService.getUserByEmail(user.email)
            if (exists != null) {
                logger.warn { "User already exists: ${user.email}" }
                ctx.response().setStatusCode(409).end("User already exists")
                return@launch
            }

            // Create and send verification code
            val token = EmailVerificationService.createVerificationToken(user.email)
            if (token != null) {
                val subject = "Verify your ProjectPilot Email"
                val content = """
                    Hello ${user.username},
                    
                    Your verification code is: $token
                    
                    Please do not share this code with anyone. It will expire in a 10 minutes.
                    
                    If you did not request this, please ignore this email.
                    
                    Regards,
                    ProjectPilot Team
                """.trimIndent()
                val sent = Mailer.sendVerificationCode(user.email, token, subject, content)
                if (sent) {
                    logger.info { "Verification code sent to ${user.email}" }
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("message", "Verification code sent to email")
                            .put("email", user.email)
                            .encode()
                        )
                } else {
                    ctx.response().setStatusCode(500).end("Failed to send email")
                }
            } else {
                ctx.response().setStatusCode(500).end("Error creating verification token")
            }

        } catch (e: Exception) {
            logger.error(e) { "Error handling signup request" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleEmailVerification(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val body = ctx.body().asJsonObject()
            val email = body.getString("email")
            val token = body.getString("token")
            val password = body.getString("password")
            val username = body.getString("username")

            if (!EmailVerificationService.verifyToken(email, token)) {
                ctx.response().setStatusCode(400).end("Invalid or expired verification token")
                return@launch
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray())
            val userToSave = User(
                email = email,
                password = hashedPassword,
                username = username,
                createAt = Instant.now(),
                bio = "",
                status = ""
            )

            val success = UserService.registerUser(userToSave)
            if (success) {
                logger.info { "Verified and registered user: $email" }
                val jwt = JwtConfig.generateToken(email, username)
                ctx.response()
                    .addCookie(Cookie.cookie("sys-token", jwt).setHttpOnly(true).setMaxAge(60 * 60))
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(201)
                    .end(JsonObject.mapFrom(SafeUser(email, username, "", "")).put("token", jwt).encode())
            } else {
                ctx.response().setStatusCode(500).end("User registration failed")
            }

        } catch (e: Exception) {
            logger.error(e) { "Error verifying email" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleSignin(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val rawBody = ctx.body().asString()

            val json = JsonObject(rawBody)
            val email = json.getString("email")
            val password = json.getString("password")

            val user = UserService.getUserByEmail(email)
            val valid = BCrypt.verifyer().verify(password.toCharArray(), user?.password?.toCharArray())
            logger.info("Login attempt for email: $email, valid: ${valid.verified}")
            if (valid.verified) {
                logger.info { "Login successful for email: $email" }
                val token = JwtConfig.generateToken(email, user?.username ?: "unknown")

                val safeUser = SafeUser(
                    email = user!!.email,
                    username = user.username,
                    bio = user.bio,
                    status = user.status
                )

                ctx.response()
                    .addCookie(
                        Cookie.cookie("sys-token", token)
                            .setHttpOnly(true)
                            .setMaxAge(60 * 60)
                    )
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(JsonObject.mapFrom(safeUser).put("token", token).encode())
            } else {
                logger.warn { "Invalid login attempt for email: $email" }
                ctx.response().setStatusCode(401).end("Invalid credentials")
            }

        } catch (e: Exception) {
            logger.error(e) { "Signin failed due to internal error" }
            ctx.response().setStatusCode(500).end("Internal error")
        }
    }
}

fun handleLogout(ctx: RoutingContext) {
    logger.info("Handling logout for user: ${ctx.get<String>("userEmail")}")
    ctx.response()
        .addCookie(
            Cookie.cookie("sys-token", "")
                .setHttpOnly(true)
                .setMaxAge(0) // Expire immediately
        )
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(JsonObject().put("message", "Logged out successfully").encode())
    logger.info("User logged out successfully: ${ctx.get<String>("userEmail")}")
}

fun handleProfile(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        val email = ctx.get<String>("userEmail")
        logger.info("Retrieving profile for user: $email")
        try {
            val user = UserService.getUserByEmail(email)
            logger.debug { "User retrieved from database: $user" }
            if (user != null) {
                val safeUser = JsonObject()
                    .put("email", user.email)
                    .put("username", user.username)
                    .put("bio", user.bio ?: "")
                    .put("status", user.status ?: "")
                    .put("createAt", user.createAt.toString())

                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(safeUser.encode())
                logger.info { "Profile retrieved successfully for user: $email" }
            } else {
                logger.warn { "User not found for email: $email" }
                ctx.response().setStatusCode(404).end("User not found")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving profile for $email" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleUpdateProfile(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        val email = ctx.get<String>("userEmail")
        val body = ctx.body().asString()
        logger.info("Updating profile for user: $email")
        try {
            val updates = mapper.readValue<Map<String, String>>(body)

            // Optionally, restrict updatable fields
            val allowedFields = listOf("username", "bio", "status")
            val filteredUpdates = updates.filterKeys { it in allowedFields }

            if (filteredUpdates.isEmpty()) {
                ctx.response().setStatusCode(400).end("No valid fields to update.")
                return@launch
            }

            UserService.updateUser(email, filteredUpdates)

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("message", "Profile updated successfully").encode())
            logger.info { "Profile updated successfully for user: $email" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update profile for $email" }
            ctx.response().setStatusCode(500).end("Error updating profile")
        }
    }
}

fun handleDeleteProfile(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        val email = ctx.get<String>("userEmail")
        logger.info("Deleting profile for user: $email")
        try {
            val deleted = UserService.deleteUserByEmail(email)
            if (deleted) {
                ctx.response()
                    .addCookie(
                        Cookie.cookie("sys-token", "")
                            .setHttpOnly(true)
                            .setMaxAge(0) // Expire immediately
                    )
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("message", "Account deleted successfully").encode())
                logger.info { "Profile deleted successfully for user: $email" }
            } else {
                logger.warn { "User not found for email: $email" }
                ctx.response().setStatusCode(404).end("User not found")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting profile for $email" }
            ctx.response().setStatusCode(500).end("Error deleting profile")
        }
    }
}

fun handleRequestPasswordReset(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val body = ctx.body().asJsonObject()
            val email = body.getString("email")

            val user = UserService.getUserByEmail(email)
            logger.info("Requesting password reset for email: $email")
            if (user == null) {
                ctx.response().setStatusCode(404).end("User not found")
                logger.warn("User not found for email: $email")
                return@launch
            }

            val token = EmailVerificationService.createVerificationToken(email)
            if (token != null) {
                val subject = "Reset your ProjectPilot password"
                val content = """
                    Hello ${user.username},
                    
                    You requested to reset your password.
                    Your reset code is: $token
                    
                    This code will expire in 10 minutes. Do not share it with anyone.
                    
                    If you did not request this, please ignore this email.
                    
                    Regards,
                    - ProjectPilot Team
                """.trimIndent()

                val sent = Mailer.sendVerificationCode(email, token, subject, content)
                logger.info("Password reset email sent to $email: $sent")
                if(sent){
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("message", "Reset code sent").encode())
                    logger.info("Password reset email sent successfully to $email")
                }
                else {
                    ctx.response().setStatusCode(500).end("Failed to send reset email")
                    logger.error("Failed to send password reset email to $email")
                }
            } else {
                ctx.response().setStatusCode(500).end("Failed to send reset email")
                logger.error("Failed to create verification token for email: $email")
            }
        } catch (e: Exception) {
            ctx.response().setStatusCode(500).end("Internal server error")
            logger.error(e) { "Error handling password reset request" }
        }
    }
}

fun handleResetPassword(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val body = ctx.body().asJsonObject()
            val email = body.getString("email")
            val token = body.getString("token")
            val newPassword = body.getString("newPassword")
            logger.info("Resetting password for email: $email")

            if (newPassword.length < 6) {
                ctx.response().setStatusCode(400).end("Password too short")
                logger.warn("Password reset failed: Password too short for email: $email")
                return@launch
            }

            val verified = EmailVerificationService.verifyToken(email, token)
            if (!verified) {
                ctx.response().setStatusCode(400).end("Invalid or expired token")
                logger.warn("Password reset failed: Invalid or expired token for email: $email")
                return@launch
            }

            val hashed = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
            val updated = UserService.updatePasswordByEmail(email, hashed)

            if (updated) {
                ctx.response().setStatusCode(200).end("Password reset successful")
                logger.info("Password reset successful for email: $email")
            } else {
                ctx.response().setStatusCode(500).end("Failed to update password")
                logger.error("Failed to update password for email: $email")
            }
        } catch (e: Exception) {
            ctx.response().setStatusCode(500).end("Internal server error")
            logger.error(e) { "Error handling password reset"}
        }
    }
}
