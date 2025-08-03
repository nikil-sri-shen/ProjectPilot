package com.projectpilot.service

import com.projectpilot.db.MongoProvider
import com.projectpilot.models.Task
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging


private val logger = KotlinLogging.logger {  }

object TaskService {
    private val client: MongoClient = MongoProvider.getClient()

    suspend fun createTask(task: Task): Int {
        return try {
            val exists = client.findOne("tasks", JsonObject().put("title", task.title), null).await()

            if (exists == null) {
                logger.info("Creating task with ID: ${task._id}")
                client.insert("tasks", JsonObject.mapFrom(task)).await()
                logger.info("Task created successfully: ${task._id}")
                try {
                    val projectUpdateResult = client.findOneAndUpdate(
                        "projects",
                        JsonObject().put("_id", task.projectId),
                        JsonObject().put(
                            "\$addToSet", JsonObject().put("tasks", task._id)
                        )
                    ).await()
                    if (projectUpdateResult != null) {
                        1 // Success
                    } else {
                        logger.warn { "Task created, but failed to update project with ID ${task.projectId}" }
                        // Rollback task creation if project update fails
                        client.removeDocument("tasks", JsonObject().put("_id", task._id)).await()
                        logger.info("Rolled back task creation for ID: ${task._id}")
                        4 // Partial success
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update project with ID ${task.projectId} after task creation." }
                    // Rollback task creation if project update fails
                    client.removeDocument("tasks", JsonObject().put("_id", task._id)).await()
                    logger.info("Rolled back task creation for ID: ${task._id}")
                    return 4 // Partial success
                }
            } else {
                logger.warn("Task with ID ${task._id} already exists.")
                2 // Already exists
            }
        } catch (e: Exception) {
            logger.error(e) { "Error creating task with ID: ${task._id}" }
            3 // Failure
        }
    }

    suspend fun updateTask(taskId: String, updates: Map<String, Any>): Int {
        return try {
            val updateJson = JsonObject(updates)
            val result = client.findOneAndUpdate(
                "tasks",
                JsonObject().put("_id", taskId),
                JsonObject().put("\$set", updateJson)
            ).await()

            if (result != null) {
                logger.info("Task with ID $taskId updated successfully.")
                1 // Success
            } else {
                logger.warn("Task with ID $taskId not found.")
                2 // Not found
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating task with ID: $taskId" }
            3 // Failure
        }
    }

    suspend fun deleteTask(taskId: String): Int {
        return try {
            val result = client.removeDocument("tasks", JsonObject().put("_id", taskId)).await()
            if (result.removedCount > 0) {
                logger.info("Task with ID $taskId deleted successfully.")
                // Remove the task from the project
                client.findOneAndUpdate(
                    "projects",
                    JsonObject().put("tasks", taskId),
                    JsonObject().put("\$pull", JsonObject().put("tasks", taskId))
                ).await()
                1 // Success
            } else {
                logger.warn("Task with ID $taskId not found.")
                2 // Not found
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting task with ID: $taskId" }
            3 // Failure
        }
    }

    suspend fun assignTask(taskId: String, assigneeEmail: String): Int {
        return try {
            val result = client.findOneAndUpdate(
                "tasks",
                JsonObject().put("_id", taskId),
                JsonObject().put("\$set", JsonObject().put("assigneeEmail", assigneeEmail))
            ).await()

            if (result != null) {
                logger.info("Task with ID $taskId assigned to $assigneeEmail successfully.")
                1 // Success
            } else {
                logger.warn("Task with ID $taskId not found.")
                2 // Not found
            }
        } catch (e: Exception) {
            logger.error(e) { "Error assigning task with ID: $taskId" }
            3 // Failure
        }
    }

    suspend fun changeTaskStatus(taskId: String, newStatus: String): Int {
        return try {
            val result = client.findOneAndUpdate(
                "tasks",
                JsonObject().put("_id", taskId),
                JsonObject().put("\$set", JsonObject().put("status", newStatus))
            ).await()

            if (result != null) {
                logger.info("Task with ID $taskId status changed to $newStatus successfully.")
                1 // Success
            } else {
                logger.warn("Task with ID $taskId not found.")
                2 // Not found
            }
        } catch (e: Exception) {
            logger.error(e) { "Error changing status of task with ID: $taskId" }
            3 // Failure
        }
    }

    suspend fun getTaskById(taskId: String): Task? {
        return try {
            val result = client.findOne("tasks", JsonObject().put("_id", taskId), null).await()
            if (result != null) {
                result.mapTo(Task::class.java)
            } else {
                logger.warn("Task with ID $taskId not found.")
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching task with ID: $taskId" }
            null
        }
    }
}