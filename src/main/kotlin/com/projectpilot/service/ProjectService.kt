package com.projectpilot.service

import com.projectpilot.db.MongoProvider
import com.projectpilot.models.Project
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging


private val logger = KotlinLogging.logger {  }

object ProjectService {
    private val client : MongoClient = MongoProvider.getClient()

    suspend fun createProject(project: Project): Int {
        try {
            val exists = client.findOne("projects", JsonObject().put("title", project.title), null).await()

            if (exists == null) {
                logger.info("Creating project with ID: ${project._id}")
                client.insert("projects", JsonObject.mapFrom(project)).await()
                logger.info("Project created successfully: ${project._id}")

                // Update the board's 'projects' array
                val boardUpdateResult = client.findOneAndUpdate(
                    "boards",
                    JsonObject().put("_id", project.boardId),
                    JsonObject().put(
                        "\$addToSet", JsonObject().put("projects", project._id)
                    )
                ).await()

                if (boardUpdateResult == null) {
                    logger.warn { "Project created, but failed to update board with ID ${project.boardId}" }
                    client.removeDocument("projects", JsonObject().put("_id", project._id)).await()
                    logger.info("Rolled back project creation for ID: ${project._id}")
                    return 4 // Partial success
                }

                return 1 // Success
            } else {
                logger.warn("Project with ID ${project._id} already exists.")
                return 2 // Already exists
            }
        } catch (e: Exception) {
            logger.error(e) { "Error creating project with ID: ${project._id}" }
            return 3 // Failure
        }
    }


    suspend fun getProjectById(projectId : String): Project? {
        logger.info("Fetching project with ID: $projectId")
        return try {
            val result = client.findOne("projects", JsonObject().put("_id", projectId), null).await()
            if (result != null) {
                result.mapTo(Project::class.java)
            } else {
                logger.warn("Project with ID $projectId not found.")
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching project with ID: $projectId" }
            null
        }
    }

    suspend fun updateProject(projectId: String, updateFields: Map<String, String>): Int {
        logger.info("Updating project with ID: $projectId")
        return try {
            val updateJson = JsonObject(updateFields)
            val updateResult = client.updateCollection(
                "projects",
                JsonObject().put("_id", projectId),
                JsonObject().put("\$set", updateJson)
            ).await()

            if (updateResult.docModified.toInt() == 0) {
                logger.warn("No project found with ID: $projectId to update")
                2 // No project found
            } else {
                logger.info("Project updated successfully: $projectId")
                1 // Update successful
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating project with ID: $projectId" }
            3 // Error occurred
        }
    }

    suspend fun deleteProject(projectId: String): Int {
        logger.info("Deleting project with ID: $projectId")
        return try {
            val deleteResult = client.removeDocument("projects", JsonObject().put("_id", projectId)).await()
            if (deleteResult.removedCount.toInt() == 0) {
                logger.warn("No project found with ID: $projectId to delete")
                2 // No project found
            } else {
                logger.info("Project deleted successfully: $projectId")
                1 // Deletion successful
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting project with ID: $projectId" }
            3 // Error occurred
        }
    }
}