package com.projectpilot.service

import com.projectpilot.db.MongoProvider
import com.projectpilot.models.Project
import com.projectpilot.models.ProjectMember
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.github.oshai.kotlinlogging.KotlinLogging


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

    suspend fun addMemberToProject(projectId: String, memberEmail: String, role: String): Int {
        logger.info("Adding member $memberEmail with role $role to project with ID: $projectId")
        return try {
            val project = getProjectById(projectId)
            if (project == null) {
                logger.warn("Project with ID $projectId not found.")
                return 2 // Project not found
            }

            if (project.members.any { it.email == memberEmail }) {
                logger.warn("Member $memberEmail already exists in project $projectId.")
                return 3 // Member already exists
            }

            val updatedMembers = project.members + ProjectMember(memberEmail, role)
            val updatedMembersJson = JsonArray(updatedMembers.map { JsonObject.mapFrom(it) })
            val updateResult = client.updateCollection(
                "projects",
                JsonObject().put("_id", projectId),
                JsonObject().put("\$set", JsonObject().put("members", updatedMembersJson))
            ).await()

            if (updateResult.docModified.toInt() > 0) {
                logger.info("Member $memberEmail added successfully to project $projectId.")
                1 // Success
            } else {
                logger.warn("Failed to add member $memberEmail to project $projectId.")
                4 // Update failed
            }
        } catch (e: Exception) {
            logger.error(e) { "Error adding member to project with ID: $projectId" }
            5 // Error occurred
        }
    }

    suspend fun removeMemberFromProject(projectId: String, memberEmail: String): Int {
        logger.info("Removing member $memberEmail from project with ID: $projectId")
        return try {
            val project = getProjectById(projectId)
            if (project == null) {
                logger.warn("Project with ID $projectId not found.")
                return 2 // Project not found
            }

            if (project.members.none { it.email == memberEmail }) {
                logger.warn("Member $memberEmail does not exist in project $projectId.")
                return 3 // Member does not exist
            }

            val updatedMembers = project.members.filter { it.email != memberEmail }
            val updatedMembersJson = JsonArray(updatedMembers.map { JsonObject.mapFrom(it) })
            val updateResult = client.updateCollection(
                "projects",
                JsonObject().put("_id", projectId),
                JsonObject().put("\$set", JsonObject().put("members", updatedMembersJson))
            ).await()

            if (updateResult.docModified.toInt() > 0) {
                logger.info("Member $memberEmail removed successfully from project $projectId.")
                1 // Success
            } else {
                logger.warn("Failed to remove member $memberEmail from project $projectId.")
                4 // Update failed
            }
        } catch (e: Exception) {
            logger.error(e) { "Error removing member from project with ID: $projectId" }
            5 // Error occurred
        }
    }

    suspend fun getMembersOfProject(projectId: String): List<ProjectMember> {
        logger.info("Fetching members of project with ID: $projectId")
        return try {
            val project = getProjectById(projectId)
            if (project != null) {
                logger.info("Members fetched successfully for project ID: $projectId")
                project.members
            } else {
                logger.warn("Project with ID $projectId not found.")
                emptyList() // Return empty list if project not found
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching members of project with ID: $projectId" }
            emptyList() // Return empty list on error
        }
    }
}
