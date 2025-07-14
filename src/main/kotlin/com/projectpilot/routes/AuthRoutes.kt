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
    authRouter.post("/signin").handler(::handleSignin)
    // ✅ Protected route using JWT middleware
    authRouter.get("/profile")
        .handler(jwtMiddleware())// custom extension
        .handler(::handleProfile)

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

            val hashedPassword = BCrypt.withDefaults().hashToString(12, user.password.toCharArray())
            var userToSave = user.copy(password = hashedPassword, createAt = Instant.now())

            val success = UserService.registerUser(userToSave)
            if (success) {
                logger.info { "User registered successfully: ${user.email}" }
                val token = JwtConfig.generateToken(user.email, user.username)

                val safeUser = SafeUser(
                    email = user.email,
                    username = user.username,
                )

                ctx.response()
                    .addCookie(
                        Cookie.cookie("sys-token", token)
                            .setHttpOnly(true)
                            .setMaxAge(60 * 60)
                    )
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(201)
                    .end(JsonObject.mapFrom(safeUser).put("token", token).encode())
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

            val user = UserService.authenticateUser(email, password)
            val valid = BCrypt.verifyer().verify(password.toCharArray(), user?.password?.toCharArray())
            logger.info("Login attempt for email: $email, valid: ${valid.verified}")
            if (valid.verified) {
                logger.info { "Login successful for email: $email" }
                val token = JwtConfig.generateToken(email, user?.username ?: "unknown")

                val safeUser = SafeUser(
                    email = user!!.email,
                    username = user.username,
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

fun handleProfile(ctx: RoutingContext) {
    val email = ctx.get<String>("userEmail") // from token
    ctx.response().end("Welcome, $email")
}