package com.projectpilot.routes

import com.projectpilot.middleware.jwtMiddleware
import com.projectpilot.models.Board
import com.projectpilot.models.BoardMember
import com.projectpilot.service.BoardService
import com.projectpilot.utils.mapper
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun boardRoutes(vertx: Vertx, router: Router) {
    val boardRouter = Router.router(vertx)

    boardRouter.post("/create").handler(jwtMiddleware()).handler(::handleCreateBoard)

    router.mountSubRouter("/board", boardRouter)
    logger.info { "Board routes registered" }
}

fun handleCreateBoard(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val email = ctx.get<String>("userEmail")
            val role = ctx.get<String>("role")

            if (email == null || role == null || role != "HOD") {
                ctx.response().setStatusCode(403).end("Only HoD can create boards")
                logger.info("$email, role $role attempted to create a board")
                logger.warn { "Unauthorized attempt to create board by $email with role $role" }
                return@launch
            }

            val rawBody = ctx.body().asJsonObject()
            val boardName = rawBody.getString("name") ?: ""
            val description = rawBody.getString("description") ?: ""
            if (boardName.isBlank() || description.isBlank()) {
                ctx.response().setStatusCode(400).end("Missing board name or description")
                return@launch
            }

            val board = Board(
                name = boardName,
                description = description,
                createdBy = email,
                members = listOf(BoardMember(email, "OWNER"))
            )
            val success = BoardService.createBoard(board)
            when (success) {
                1 -> {
                    ctx.response().setStatusCode(201).end("Board created")
                    logger.info { "Board created successfully by $email: ${board.name}" }
                }
                2 -> {
                    ctx.response().setStatusCode(409).end("Board already exists")
                    logger.warn { "Board already exists: ${board.name}" }
                }
                else -> {
                    ctx.response().setStatusCode(500).end("Failed to create board")
                    logger.error { "Failed to create board: ${board.name}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error creating board" }
            ctx.response().setStatusCode(400).end("Invalid data")
        }
    }
}
