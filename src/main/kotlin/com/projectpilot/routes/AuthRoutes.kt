package com.projectpilot.routes

import com.fasterxml.jackson.module.kotlin.readValue
import com.projectpilot.models.User
import com.projectpilot.service.UserService
import com.projectpilot.utils.mapper
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun authRoutes(vertx: Vertx, router: Router) {
    val authRouter = Router.router(vertx)

    authRouter.post("/signup").handler(::handleSignup)
    authRouter.post("/signin").handler(::handleSignin)

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
                ctx.response().setStatusCode(400).end("Invalid email or password too short")
                return@launch
            }

            val success = UserService.registerUser(user)
            if (success) {
                logger.info { "User registered successfully: ${user.email}" }
                ctx.response().setStatusCode(201).end("User registered")
            } else {
                logger.warn { "User already exists: ${user.email}" }
                ctx.response().setStatusCode(409).end("User already exists")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling signup request" }
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

            val valid = UserService.authenticateUser(email, password)
            if (valid) {
                logger.info { "Login successful for email: $email" }
                ctx.response().end("Login successful")
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
