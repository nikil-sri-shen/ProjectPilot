package com.projectpilot.routes

import com.projectpilot.middleware.authorizeRole
import com.projectpilot.middleware.jwtMiddleware
import com.projectpilot.models.Task
import com.projectpilot.service.ProjectService
import com.projectpilot.service.TaskService
import com.projectpilot.utils.CommonUtils
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject.mapFrom
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {  }

fun taskRoutes(vertx: Vertx, router: Router) {
    val taskRouter = Router.router(vertx)

    taskRouter.post("/create").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor", "student")).handler(::handleCreateTask)
    taskRouter.get("/:taskId").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor", "student")).handler(::handleGetTaskById)
    taskRouter.delete("/:taskId").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor", "student")).handler(::handleDeleteTask)
    taskRouter.post("/:taskId/update").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor","student")).handler(::handleUpdateTask)
    taskRouter.post("/:taskId/assign").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor")).handler(::handleAssignTask)
    taskRouter.post("/:taskId/status").handler(jwtMiddleware()).handler(authorizeRole("admin", "professor", "student")).handler(::handleUpdateTaskStatus)

    router.mountSubRouter("/tasks", taskRouter)
    logger.info { "Task routes registered" }
}

fun handleCreateTask(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val rawBody = ctx.body().asJsonObject()
            val projectId = rawBody.getString("projectId")
            val title = rawBody.getString("title")
            val description = rawBody.getString("description")
            if (projectId.isNullOrEmpty()){
                ctx.response().setStatusCode(400).end("Project ID is required")
                return@launch
            }
            if(title.isNullOrEmpty() || description.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("Title and Description is required")
                return@launch
            }
            val taskId = "${projectId}~${ CommonUtils.generateUniqueTaskId() }"
            val task = Task(
                _id = taskId,
                projectId = projectId,
                title = title,
                description = description
            )
            val result = TaskService.createTask(task)

            when (result) {
                1 -> {
                    ctx.response().setStatusCode(201).end("Task created successfully")
                    logger.info { "Task created successfully: ${task._id}" }
                }
                2 -> {
                    ctx.response().setStatusCode(409).end("Task already exists")
                    logger.warn { "Task with title '${task.title}' already exists." }
                }
                else -> {
                    ctx.response().setStatusCode(500).end("Failed to create task")
                    logger.error { "Failed to create task: ${task._id}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error creating task" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleUpdateTask(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val taskId = ctx.pathParam("taskId")
            if (taskId.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("Task ID is required")
                return@launch
            }
            val rawBody = ctx.body().asJsonObject()
            val updateFields = rawBody.map.entries.associate { it.key to it.value.toString() }

            if (updateFields.isEmpty()) {
                ctx.response().setStatusCode(400).end("No updates provided")
                return@launch
            }

            val result = TaskService.updateTask(taskId, updateFields)

            when (result) {
                1 -> {
                    ctx.response().setStatusCode(200).end("Task updated successfully")
                    logger.info { "Task updated successfully: $taskId" }
                }
                2 -> {
                    ctx.response().setStatusCode(404).end("Task not found")
                    logger.warn { "Task with ID '$taskId' not found." }
                }
                else -> {
                    ctx.response().setStatusCode(500).end("Failed to update task")
                    logger.error { "Failed to update task: $taskId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating task" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleDeleteTask(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val taskId = ctx.pathParam("taskId")
            if (taskId.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("Task ID is required")
                return@launch
            }

            val result = TaskService.deleteTask(taskId)

            when (result) {
                1 -> {
                    ctx.response().setStatusCode(200).end("Task deleted successfully")
                    logger.info { "Task deleted successfully: $taskId" }
                }
                2 -> {
                    ctx.response().setStatusCode(404).end("Task not found")
                    logger.warn { "Task with ID '$taskId' not found." }
                }
                else -> {
                    ctx.response().setStatusCode(500).end("Failed to delete task")
                    logger.error { "Failed to delete task: $taskId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting task" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleGetTaskById(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val taskId = ctx.pathParam("taskId")
            if (taskId.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("Task ID is required")
                return@launch
            }

            val task = TaskService.getTaskById(taskId)

            if (task != null) {
                ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(mapFrom(task).encodePrettily())
                logger.info { "Task fetched successfully: $taskId" }
            } else {
                ctx.response().setStatusCode(404).end("Task not found")
                logger.warn { "Task with ID '$taskId' not found." }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching task by ID" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleAssignTask(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val taskId = ctx.pathParam("taskId")
            if (taskId.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("Task ID is required")
                return@launch
            }
            val rawBody = ctx.body().asJsonObject()
            val assigneeEmail = rawBody.getString("assigneeEmail")
            val projectId = rawBody.getString("projectId")
            if (assigneeEmail.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("Assignee email is required")
                return@launch
            }

            val projectMember = ProjectService.getMembersOfProject(projectId)
            if (projectMember.none { it.email == assigneeEmail }) {
                ctx.response().setStatusCode(400).end("Assignee email is not a member of the project")
                return@launch
            }

            val result = TaskService.assignTask(taskId, assigneeEmail)

            when (result) {
                1 -> {
                    ctx.response().setStatusCode(200).end("Task assigned successfully")
                    logger.info { "Task assigned successfully: $taskId to $assigneeEmail" }
                }
                2 -> {
                    ctx.response().setStatusCode(404).end("Task not found")
                    logger.warn { "Task with ID '$taskId' not found." }
                }
                else -> {
                    ctx.response().setStatusCode(500).end("Failed to assign task")
                    logger.error { "Failed to assign task: $taskId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error assigning task" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}

fun handleUpdateTaskStatus(ctx: RoutingContext) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val taskId = ctx.pathParam("taskId")
            if (taskId.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("Task ID is required")
                return@launch
            }
            val rawBody = ctx.body().asJsonObject()
            val newStatus = rawBody.getString("status")
            if (newStatus.isNullOrEmpty()) {
                ctx.response().setStatusCode(400).end("New status is required")
                return@launch
            }

            val result = TaskService.changeTaskStatus(taskId, newStatus)

            when (result) {
                1 -> {
                    ctx.response().setStatusCode(200).end("Task status updated successfully")
                    logger.info { "Task status updated successfully: $taskId to $newStatus" }
                }
                2 -> {
                    ctx.response().setStatusCode(404).end("Task not found")
                    logger.warn { "Task with ID '$taskId' not found." }
                }
                else -> {
                    ctx.response().setStatusCode(500).end("Failed to update task status")
                    logger.error { "Failed to update task status: $taskId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating task status" }
            ctx.response().setStatusCode(500).end("Internal Server Error")
        }
    }
}