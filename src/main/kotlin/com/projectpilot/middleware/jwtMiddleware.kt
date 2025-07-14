package com.projectpilot.middleware

import com.projectpilot.utils.JwtConfig
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Route

fun jwtMiddleware() : Handler<RoutingContext> = Handler { ctx ->
    val token = ctx.request().getHeader("Authorization")?.removePrefix("Bearer ")
        ?: ctx.request().getHeader("Cookie")?.split(";")
            ?.firstOrNull { it.trim().startsWith("sys-token=") }
            ?.substringAfter("sys-token=")

    if (token.isNullOrEmpty()) {
        ctx.response().setStatusCode(401).end("Unauthorized: No token provided")
        return@Handler
    }

    try {
        val claims = JwtConfig.validateToken(token)
        if (claims != null) {
            ctx.put("userEmail", claims["email"])
            ctx.put("username", claims["username"])
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