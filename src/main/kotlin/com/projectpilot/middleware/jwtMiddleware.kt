package com.projectpilot.middleware

import com.projectpilot.utils.AppRoles
import com.projectpilot.utils.JwtConfig
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun jwtMiddleware() : Handler<RoutingContext> = Handler { ctx ->
    val token = ctx.request().getHeader("Authorization")?.removePrefix("Bearer ")
        ?: ctx.request().getHeader("Cookie")?.split(";")
            ?.firstOrNull { it.trim().startsWith("sys-token=") }
            ?.substringAfter("sys-token=")

    if (token.isNullOrEmpty()) {
        logger.warn("Unauthorized access attempt: No token provided $token")
        ctx.response().setStatusCode(401).end("Unauthorized: No token provided")
        return@Handler
    }

    try {
        val claims = JwtConfig.validateToken(token)
        if (claims != null) {
            val role = claims["role"]
            if (!AppRoles.isValid(role)) {
                ctx.response().setStatusCode(401).end("Unauthorized: Invalid role claim")
                return@Handler
            }
            ctx.put("userEmail", claims["email"])
            ctx.put("username", claims["username"])
            ctx.put("role", role)
            ctx.next()
        } else {
            ctx.response().setStatusCode(401).end("Unauthorized: Invalid token")
            return@Handler
        }
    } catch (e: Exception) {
        ctx.response().setStatusCode(401).end("Unauthorized: Token parsing error")
        return@Handler
    }
}
