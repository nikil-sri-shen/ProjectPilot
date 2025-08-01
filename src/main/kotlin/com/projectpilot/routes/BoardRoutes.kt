package com.projectpilot.routes

import com.projectpilot.middleware.authorizeRole
import com.projectpilot.middleware.jwtMiddleware
import com.projectpilot.models.Board
import com.projectpilot.models.BoardMember
import com.projectpilot.service.BoardService
import com.projectpilot.utils.CommonUtils
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

    boardRouter.post("/create").handler(jwtMiddleware()).handler(authorizeRole("admin")).handler(::handleCreateBoard)
    boardRouter.get("/myBoards").handler(jwtMiddleware()).handler(authorizeRole("admin","professor")).handler(::handleMyBoards)
    boardRouter.get("/boards").handler(jwtMiddleware()).handler(authorizeRole("admin","professor")).handler(::handleBoards)
    boardRouter.get("/:boardId").handler(jwtMiddleware()).handler(authorizeRole("admin","professor")).handler(::handleBoardsWithId)
    boardRouter.delete("/:boardId").handler(jwtMiddleware()).handler(authorizeRole("admin")).handler(::handleDeleteBoard)
    boardRouter.post("/:boardId/update").handler(jwtMiddleware()).handler(authorizeRole("admin")).handler(::handleBoardUpdate)
    boardRouter.post("/:boardId/invite").handler(jwtMiddleware()).handler(authorizeRole("admin")).handler(::handlerSendInvite)
    boardRouter.post("/:boardId/verify-invite").handler(jwtMiddleware()).handler(authorizeRole("admin","professor")).handler(::handlerVerifyInvite)
    boardRouter.get("/:boardId/members").handler(jwtMiddleware()).handler(authorizeRole("admin","professor")).handler(::handleBoardMembers)
    boardRouter.post("/:boardId/members").handler(jwtMiddleware()).handler(authorizeRole("admin")).handler(::handleRemoveBoardMembers)
    boardRouter.get("/:boardId/summary").handler(jwtMiddleware()).handler(authorizeRole("admin","professor")).handler(::handleBoardSummary)
    boardRouter.get("/:boardId/projects").handler(jwtMiddleware()).handler(authorizeRole("admin","professor")).handler(::handleProjectsByBoardId)

    router.mountSubRouter("/board", boardRouter)
    logger.info { "Board routes registered" }
}

fun handleCreateBoard(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val email = ctx.get<String>("userEmail")
            val rawBody = ctx.body().asJsonObject()
            val boardName = rawBody.getString("name") ?: ""
            val description = rawBody.getString("description") ?: ""
            if (boardName.isBlank() || description.isBlank()) {
                ctx.response().setStatusCode(400).end("Missing board name or description")
                return@launch
            }
            val boardId = CommonUtils.generateUniqueBoardId()
            val board = Board(
                _id = boardId,
                name = boardName,
                description = description,
                createdBy = email,
                members = listOf(BoardMember(email, "ADMIN"))
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

fun handleMyBoards(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val email = ctx.get<String>("userEmail")
            if (email == null) {
                ctx.response().setStatusCode(403).end("Unauthorized access")
                logger.warn { "Unauthorized access attempt by null email" }
                return@launch
            }

            val boards = BoardService.getBoardForUser(email)
            if (boards.isEmpty()) {
                ctx.response().setStatusCode(404).end("No boards found for user $email")
                logger.info { "No boards found for user: $email" }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(mapper.writeValueAsString(boards))
                logger.info { "Boards retrieved successfully for user: $email" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving boards for user" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handleBoardsWithId(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val email = ctx.get<String>("userEmail")
            if (email == null) {
                ctx.response().setStatusCode(403).end("Unauthorized access")
                logger.warn { "Unauthorized access attempt by null email" }
                return@launch
            }

            val boardId = ctx.pathParam("boardId")
            if (boardId.isBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                logger.warn { "Board ID is missing in request" }
                return@launch
            }

            val board = BoardService.getBoardById(boardId)
            if (board == null) {
                ctx.response().setStatusCode(404).end("Board not found")
                logger.info { "Board not found with ID: $boardId" }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(mapper.writeValueAsString(board))
                logger.info { "Board retrieved successfully: ${board.name}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving board with ID: ${ctx.pathParam("boardId")}" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handleBoardUpdate(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val email = ctx.get<String>("userEmail")
            if (email == null) {
                ctx.response().setStatusCode(403).end("Unauthorized access")
                logger.warn { "Unauthorized access attempt by null email" }
                return@launch
            }

            val boardId = ctx.pathParam("boardId")
            if (boardId.isBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                logger.warn { "Board ID is missing in request" }
                return@launch
            }

            val rawBody = ctx.body().asJsonObject()
            val updatedFields = rawBody.map.entries.associate { it.key to it.value.toString() }

            if (updatedFields.isEmpty()) {
                ctx.response().setStatusCode(400).end("No fields provided for update")
                logger.warn { "No fields provided for update for board ID: $boardId" }
                return@launch
            }

            val success = BoardService.updateBoard(boardId, updatedFields)
            if (success) {
                ctx.response().setStatusCode(200).end("Board updated successfully")
                logger.info { "Board updated successfully: $boardId" }
            } else {
                ctx.response().setStatusCode(404).end("Board not found or update failed")
                logger.warn { "Failed to update board with ID: $boardId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating board with ID: ${ctx.pathParam("boardId")}" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handlerSendInvite(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val senderRole = ctx.get<String>("role")?.lowercase()
            senderRole
            if(senderRole != "admin") {
                ctx.response().setStatusCode(403).end("Only Admin can send invites")
                logger.warn { "Unauthorized attempt to send invite by user with role $senderRole" }
                return@launch
            }
            val boardId = ctx.pathParam("boardId")
            val body = ctx.body().asJsonObject()
            val toEmail = body.getString("email")
            val role = body.getString("role")

            val success = BoardService.sendBoardInvite(boardId, toEmail, role)
            when (success) {
                1 -> {
                    ctx.response().setStatusCode(404).end("Board not found $boardId")
                    logger.info { "Board not found: $boardId" }
                }
                2 -> {
                    ctx.response().setStatusCode(409).end("User not exists")
                    logger.info { "User $toEmail not exists" }
                }
                3 -> {
                    ctx.response().setStatusCode(500).end("Invite code not generated")
                    logger.info { "Invite code not generated" }
                }
                4 -> {
                    ctx.response().setStatusCode(200).end("Invite sent successfully")
                    logger.info { "Invite sent successfully to $toEmail for board $boardId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error sending invite" }
            ctx.response().setStatusCode(400).end("Invalid request")
        }
    }
}

fun handlerVerifyInvite(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val boardId = ctx.pathParam("boardId")
            val body = ctx.body().asJsonObject()
            val email = body.getString("email")
            val token = body.getString("token")
            val role = body.getString("role") // Might be passed again or saved in DB

            val success = BoardService.verifyBoardInvite(boardId, email, token, role)
            if (success) {
                ctx.response().setStatusCode(200).end("Joined board successfully")
            } else {
                ctx.response().setStatusCode(400).end("Invalid or expired token")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error verifying invite" }
            ctx.response().setStatusCode(400).end("Invalid request")
        }
    }
}

fun handleBoardMembers(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val boardId = ctx.pathParam("boardId")
            if (boardId.isBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                logger.warn { "Board ID is missing in request" }
                return@launch
            }

            val members = BoardService.getBoardMembers(boardId)
            if (members.isEmpty()) {
                ctx.response().setStatusCode(404).end("No members found for board $boardId")
                logger.info { "No members found for board: $boardId" }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(mapper.writeValueAsString(members))
                logger.info { "Members retrieved successfully for board: $boardId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving members for board with ID: ${ctx.pathParam("boardId")}" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handleRemoveBoardMembers(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val role = ctx.get<String>("role")?.lowercase()
            if(role != "admin") {
                ctx.response().setStatusCode(403).end("Only Admin can remove members")
                logger.warn { "Unauthorized attempt to remove members by user with role $role" }
                return@launch
            }
            val boardId = ctx.pathParam("boardId")
            if (boardId.isBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                logger.warn { "Board ID is missing in request" }
                return@launch
            }

            val body = ctx.body().asJsonObject()
            val emailToRemove = body.getString("email")

            if (emailToRemove.isEmpty()) {
                ctx.response().setStatusCode(400).end("No emails provided for removal")
                logger.warn { "No emails provided for removal from board: $boardId" }
                return@launch
            }

            val success = BoardService.removeMemberFromBoard(boardId, emailToRemove)
            if (success) {
                ctx.response().setStatusCode(200).end("Members removed successfully")
                logger.info { "Members removed successfully from board: $boardId" }
            } else {
                ctx.response().setStatusCode(404).end("Board not found or no members removed")
                logger.warn { "Failed to remove members from board with ID: $boardId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error removing members from board with ID: ${ctx.pathParam("boardId")}" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handleBoardSummary(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val boardId = ctx.pathParam("boardId")
            if (boardId.isBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                logger.warn { "Board ID is missing in request" }
                return@launch
            }

            val summary = BoardService.getBoardSummary(boardId)
            if (summary == null) {
                ctx.response().setStatusCode(404).end("Board not found")
                logger.info { "Board not found with ID: $boardId" }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(mapper.writeValueAsString(summary))
                logger.info { "Board summary retrieved successfully for board: $boardId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving board summary for ID: ${ctx.pathParam("boardId")}" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handleBoards(ctx: RoutingContext){
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val email = ctx.get<String>("userEmail")
            if (email == null) {
                ctx.response().setStatusCode(403).end("Unauthorized access")
                logger.warn { "Unauthorized access attempt by null email" }
                return@launch
            }

            val boards = BoardService.getAllBoards()
            if (boards.isEmpty()) {
                ctx.response().setStatusCode(404).end("No boards found")
                logger.info { "No boards found in the system" }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(mapper.writeValueAsString(boards))
                logger.info { "All boards retrieved successfully for user: $email" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving all boards" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handleDeleteBoard(ctx: RoutingContext){
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val email = ctx.get<String>("userEmail")
            if (email == null) {
                ctx.response().setStatusCode(403).end("Unauthorized access")
                logger.warn { "Unauthorized access attempt by null email" }
                return@launch
            }

            val boardId = ctx.pathParam("boardId")
            if (boardId.isBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                logger.warn { "Board ID is missing in request" }
                return@launch
            }

            val success = BoardService.deleteBoard(boardId)
            if (success) {
                ctx.response().setStatusCode(204).end("Board deleted successfully")
                logger.info { "Board deleted successfully: $boardId" }
            } else {
                ctx.response().setStatusCode(404).end("Board not found or deletion failed")
                logger.warn { "Failed to delete board with ID: $boardId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting board with ID: ${ctx.pathParam("boardId")}" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}

fun handleProjectsByBoardId(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val boardId = ctx.pathParam("boardId")
            if (boardId.isBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                logger.warn { "Board ID is missing in request" }
                return@launch
            }

            val projects = BoardService.getProjectsWithBoardId(boardId)
            if (projects.isEmpty()) {
                ctx.response().setStatusCode(404).end("No projects found for board $boardId")
                logger.info { "No projects found for board: $boardId" }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(mapper.writeValueAsString(projects))
                logger.info { "Projects retrieved successfully for board: $boardId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving projects for board with ID: ${ctx.pathParam("boardId")}" }
            ctx.response().setStatusCode(500).end("Internal server error")
        }
    }
}