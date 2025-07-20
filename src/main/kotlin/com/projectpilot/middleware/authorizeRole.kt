package com.projectpilot.middleware

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext

private val logger = mu.KotlinLogging.logger {}

fun authorizeRole(vararg allowedRoles: String): Handler<RoutingContext> = Handler { ctx ->
    val userRole = ctx.get<String>("role")?.lowercase()
    if (userRole == null || userRole !in allowedRoles.map { it.lowercase() }) {
        logger.warn { "Access denied for role: $userRole. Allowed: ${allowedRoles.joinToString()}" }
        ctx.response().setStatusCode(403).end("Forbidden: Insufficient permissions")
        return@Handler
    }
    ctx.next()
}
