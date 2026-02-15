package com.projectpilot.routes

import com.projectpilot.middleware.authorizeRole
import com.projectpilot.middleware.jwtMiddleware
import com.projectpilot.models.Project
import com.projectpilot.models.ProjectMember
import com.projectpilot.service.BoardService
import com.projectpilot.service.ProjectService
import com.projectpilot.utils.CommonUtils
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject.mapFrom
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.oshai.kotlinlogging.KotlinLogging
import org.bson.json.JsonObject

private val logger = KotlinLogging.logger {  }

fun projectRoutes(vertx: Vertx, router: Router) {
    val projectRouter = Router.router(vertx)

    projectRouter.post("/create").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor")).handler(::handleCreateProject)
    projectRouter.get("/:projectId").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor")).handler(::handleGetProjectById)
    projectRouter.post("/:projectId").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor")).handler(::handleUpdateProject)
    projectRouter.delete("/:projectId").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor")).handler(::handleDeleteProject)
//    projectRouter.get("/:projectId/members").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor", "student")).handler(::handleGetProjectMembers)
    projectRouter.post("/:projectId/addMember").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor")).handler(::handleAddMemberToProject)
    projectRouter.delete("/:projectId/removeMember").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor")).handler(::handleRemoveProjectMember)

    // mount sub-router under /projects
    router.route("/projects/*").subRouter(projectRouter)
    logger.info("Project routes registered")
}

fun handleCreateProject(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            logger.info("Handling project creation request")
            val email = ctx.get<String>("userEmail")
            val role = ctx.get<String>("role")
            val rawBody = ctx.body().asJsonObject()
            val boardId = rawBody.getString("boardId")
            if (boardId.isNullOrBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                return@launch
            }
            if(!BoardService.findBoardExists(boardId)) {
                ctx.response().setStatusCode(404).end("Board not found with ID: $boardId")
                return@launch
            }
            val projectName = rawBody.getString("name")
            val projectDescription = rawBody.getString("description")
            if (projectName.isNullOrBlank() || projectDescription.isNullOrBlank()) {
                ctx.response().setStatusCode(400).end("Project name and description are required")
                return@launch
            }
            logger.info("Creating project for boardId: $boardId by user: $email with role: $role")
            val projectId = "${boardId}~${CommonUtils.generateUniqueProjectId()}"
            val project = Project(
                _id = projectId,
                boardId = boardId,
                title = projectName,
                description = projectDescription,
                createdBy = email,
                members = listOf(ProjectMember(email, role)),
                status = "On going"
            )
            val result = ProjectService.createProject(project)
            when (result) {
                1 -> ctx.response().setStatusCode(201).end("Project created successfully")
                2 -> ctx.response().setStatusCode(409).end("Project already exists")
                3 -> ctx.response().setStatusCode(500).end("Failed to create project")
                4 -> ctx.response().setStatusCode(500).end("Project created, but failed to update board. Rolled back project creation.")
                else -> ctx.response().setStatusCode(500).end("Internal Server Error")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error creating project" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleGetProjectById(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            logger.info("Handling request to get project by ID")
            val projectId = ctx.pathParam("projectId")
            if (projectId.isNullOrBlank()) {
                ctx.response().setStatusCode(400).end("Project ID is required")
                return@launch
            }
            logger.info("Fetching project with ID: $projectId")
            val project = ProjectService.getProjectById(projectId)
            if (project != null) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end((mapFrom(project)).encodePrettily())
            } else {
                ctx.response().setStatusCode(404).end("Project not found with ID: $projectId")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching project by ID" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleUpdateProject(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            logger.info("Handling request to update project")
            val projectId = ctx.pathParam("projectId")
            if (projectId.isNullOrBlank()) {
                ctx.response().setStatusCode(400).end("Project ID is required")
                return@launch
            }

            val rawBody = ctx.body().asJsonObject()
            val updateFields = rawBody.map.entries.associate { it.key to it.value.toString() }

            val updateResult = ProjectService.updateProject(projectId, updateFields)

            when (updateResult) {
                1 -> ctx.response().setStatusCode(200).end("Project updated successfully")
                2 -> ctx.response().setStatusCode(404).end("Project not found with ID: $projectId")
                else -> ctx.response().setStatusCode(500).end("Failed to update project")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating project" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleDeleteProject(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            logger.info("Handling request to delete project")
            val projectId = ctx.pathParam("projectId")
            if (projectId.isNullOrBlank()) {
                ctx.response().setStatusCode(400).end("Project ID is required")
                return@launch
            }

            val deleteResult = ProjectService.deleteProject(projectId)

            when (deleteResult) {
                1 -> ctx.response().setStatusCode(200).end("Project deleted successfully")
                2 -> ctx.response().setStatusCode(404).end("Project not found with ID: $projectId")
                else -> ctx.response().setStatusCode(500).end("Failed to delete project")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting project" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleAddMemberToProject(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val rawBody = ctx.body().asJsonObject()
            val boardId = rawBody.getString("boardId")
            val projectId = ctx.pathParam("projectId")
            val memberEmail = rawBody.getString("memberEmail")
            if (boardId.isNullOrEmpty()){
                ctx.response().setStatusCode(400).end("Board ID is required")
                return@launch
            }
            try {
                val members = BoardService.getBoardMembers(boardId)
                if(memberEmail in members){
                    logger.info("Adding member $memberEmail to project with ID: $projectId")
                    val role = rawBody.getString("role") ?: "STUDENT"
                    val addResult = ProjectService.addMemberToProject(projectId, memberEmail, role)

                    when (addResult) {
                        1 -> ctx.response().setStatusCode(200).end("Member added successfully")
                        2 -> ctx.response().setStatusCode(404).end("Project not found with ID: $projectId")
                        3 -> ctx.response().setStatusCode(409).end("Member already exists in the project")
                        else -> ctx.response().setStatusCode(500).end("Failed to add member to project")
                    }
                } else {
                    ctx.response().setStatusCode(404).end("Member not found in board with ID: $boardId")
                }
            } catch (e: Exception) {
                logger.error(e) { "Error checking board existence" }
                ctx.response().setStatusCode(500).end("Internal Server Error")
                return@launch
            }
        } catch (e: Exception) {
            logger.error(e) { "Error adding member to project" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleRemoveProjectMember(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val projectId = ctx.pathParam("projectId")
            val rawBody = ctx.body().asJsonObject()
            val boardId = rawBody.getString("boardId")
            val memberEmail = rawBody.getString("memberEmail")

            if (projectId.isNullOrBlank() || memberEmail.isNullOrBlank()) {
                ctx.response().setStatusCode(400).end("Project ID and member email are required")
                return@launch
            }

            if (boardId.isNullOrBlank()) {
                ctx.response().setStatusCode(400).end("Board ID is required")
                return@launch
            }

            try {
                val members = BoardService.getBoardMembers(boardId)
                if (memberEmail !in members) {
                    ctx.response().setStatusCode(404).end("Member not found in board with ID: $boardId")
                    return@launch
                }
                logger.info("Removing member $memberEmail from project with ID: $projectId")
                val removeResult = ProjectService.removeMemberFromProject(projectId, memberEmail)

                when (removeResult) {
                    1 -> ctx.response().setStatusCode(200).end("Member removed successfully")
                    2 -> ctx.response().setStatusCode(404).end("Project not found with ID: $projectId")
                    3 -> ctx.response().setStatusCode(404).end("Member not found in the project")
                    4 -> ctx.response().setStatusCode(500).end("Failed to update project members")
                    else -> ctx.response().setStatusCode(500).end("Failed to remove member from project")
                }
            } catch (e: Exception) {
                logger.error(e) { "Error checking board existence" }
                ctx.response().setStatusCode(500).end("Internal Server Error")
                return@launch
            }
        } catch (e: Exception) {
            logger.error(e) { "Error removing member from project" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}
