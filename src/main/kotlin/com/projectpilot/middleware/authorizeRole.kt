package com.projectpilot.middleware

import com.projectpilot.utils.AppRoles
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun authorizeRole(vararg allowedRoles: String): Handler<RoutingContext> = Handler { ctx ->
    val userRole = ctx.get<String>("role")
    if (!AppRoles.matches(userRole, allowedRoles.toList())) {
        logger.warn { "Access denied for role: $userRole. Allowed: ${allowedRoles.joinToString()}" }
        ctx.response().setStatusCode(403).end("Forbidden: Insufficient permissions")
        return@Handler
    }
    ctx.next()
}
