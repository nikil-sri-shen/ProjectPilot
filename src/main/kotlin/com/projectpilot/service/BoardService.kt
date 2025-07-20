package com.projectpilot.service

import com.projectpilot.db.MongoProvider
import com.projectpilot.models.Board
import com.projectpilot.utils.Mailer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object BoardService {
    private val client: MongoClient = MongoProvider.getClient()

    suspend fun generateUniqueBoardId(): String {
        var id: String
        do {
            id = (100000..999999).random().toString()
            val exists = client.findOne("boards", JsonObject().put("_id", id), null).await() != null
        } while (exists)
        return id
    }

    suspend fun createBoard(board: Board): Int {
        val exists = client.findOne("boards", JsonObject().put("name", board.name), null).await()
        try {
            return if (exists == null) {
                logger.info { "Creating new board: ${board.name}" }
                client.insert("boards", JsonObject.mapFrom(board)).await()
                logger.debug { "Board created successfully: ${board.name}" }
                1
            } else {
                logger.warn { "Board already exists with name: ${board.name}" }
                2
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create board: ${board.name}" }
            return 3
        }
    }

    suspend fun getBoardForUser(email: String): List<Board> {
        val query = JsonObject().put("members.email", email)
        return try {
            val results = client.find("boards", query).await()
            if (results.isEmpty()) {
                logger.warn { "No boards found for user: $email" }
                emptyList()
            } else {
                logger.info { "Found ${results.size} boards for user: $email" }
                results.map { it.mapTo(Board::class.java) }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve boards for user: $email" }
            emptyList()
        }
    }

    suspend fun getBoardById(boardId: String): Board? {
        return try {
            val result = client.findOne("boards", JsonObject().put("_id", boardId), null).await()
            if (result != null) {
                logger.info { "Board found with ID: $boardId" }
                result.mapTo(Board::class.java)
            } else {
                logger.warn { "No board found with ID: $boardId" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve board by ID: $boardId" }
            null
        }
    }

    suspend fun updateBoard(boardId: String, updatedFields: Map<String, Any>): Boolean {
        if (updatedFields.isEmpty()) {
            logger.warn { "No fields provided for update for board ID: $boardId" }
            return false
        }
        try {
            val updateObject = JsonObject().apply {
                updatedFields.forEach { (key, value) -> put(key, value) }
            }
            val result = client.updateCollection(
                "boards",
                JsonObject().put("_id", boardId),
                JsonObject().put("\$set", updateObject)
            ).await()
            if (result.docModified.toInt() == 0) {
                logger.warn { "No board found to update with ID: $boardId" }
                return false
            }
            logger.info { "Board updated successfully with ID: $boardId" }
            return true
        } catch (e: Exception) {
            logger.error(e) { "Failed to update board with ID: $boardId" }
            return false
        }
    }

    suspend fun sendBoardInvite(boardId: String, toEmail: String, role: String): Int {
        try {
            val board = client.findOne("boards", JsonObject().put("_id", boardId), null).await()
            if (board == null) {
                logger.error { "Board not found: $boardId" }
                return 1
            }

            val memberExists = client.findOne("users", JsonObject().put("email", toEmail), null).await()
            if (memberExists == null) {
                logger.warn { "User not exists: $toEmail" }
                return 2
            }

            val code = EmailService.createVerificationToken(toEmail)
            if (code == null) return 3

            val boardName = board.getString("name")
            val subject = "Invitation to join board: $boardName"
            val content = """
            You have been invited to join the board "$boardName" as $role.
            
            Your verification code is: $code

            Please use this code in the ProjectPilot app to accept the invitation.
            This code will expire in 10 minutes.
        """.trimIndent()

            if(Mailer.sendVerificationCode(toEmail, code, subject, content)) return 4 // success
        } catch (e: Exception) {
            logger.error(e) { "Failed to send board invite to $toEmail" }
            return 5 // error
        }
        return 0 // default case, should not happen
    }

    suspend fun verifyBoardInvite(boardId: String, email: String, token: String, role: String): Boolean {
        val isValid = EmailService.verifyToken(email, token)
        if (!isValid) return false

        return addMemberToBoard(boardId, email, role)
    }

    suspend fun addMemberToBoard(boardId: String, memberEmail: String, role: String): Boolean {
        try {
            val board = client.findOne("boards", JsonObject().put("_id", boardId), null).await()
            if (board == null) {
                logger.error { "Board not found with ID: $boardId" }
                return false
            }
            val members = board.getJsonArray("members") ?: JsonArray()
            if (members.any { (it as? JsonObject)?.getString("email") == memberEmail }) {
                logger.warn { "Member already exists in board: $memberEmail" }
                return false
            }
            val newMember = JsonObject().put("email", memberEmail).put("role", role)
            members.add(newMember)
            val updateResult = client.updateCollection(
                "boards",
                JsonObject().put("_id", boardId),
                JsonObject().put("\$set", JsonObject().put("members", members))
            ).await()
            if (updateResult.docModified.toInt() == 1) {
                logger.info { "Member added to board successfully: $memberEmail" }
                return true
            } else {
                logger.warn { "Failed to add member to board: $memberEmail" }
                return false
            }
        } catch (e: Exception) {
            logger.error(e) { "Error adding member to board: $memberEmail" }
            return false
        }
    }

    suspend fun removeMemberFromBoard(boardId: String, memberEmail: String): Boolean {
        try {
            val board = client.findOne("boards", JsonObject().put("_id", boardId), null).await()
            if (board == null) {
                logger.error { "Board not found with ID: $boardId" }
                return false
            }
            val members = board.getJsonArray("members") ?: JsonArray()
            val memberToRemove = members.firstOrNull { (it as? JsonObject)?.getString("email") == memberEmail }
            if (memberToRemove == null) {
                logger.warn { "Member not found in board: $memberEmail" }
                return false
            }
            members.remove(memberToRemove)
            val updateResult = client.updateCollection(
                "boards",
                JsonObject().put("_id", boardId),
                JsonObject().put("\$set", JsonObject().put("members", members))
            ).await()
            if (updateResult.docModified.toInt() == 1) {
                logger.info { "Member removed from board successfully: $memberEmail" }
                return true
            } else {
                logger.warn { "Failed to remove member from board: $memberEmail" }
                return false
            }
        } catch (e: Exception) {
            logger.error(e) { "Error removing member from board: $memberEmail" }
            return false
        }
    }

    suspend fun getBoardMembers(boardId: String): List<String> {
        return try {
            val board = client.findOne("boards", JsonObject().put("_id", boardId), null).await()
            if (board != null) {
                val members = board.getJsonArray("members") ?: JsonArray()
                members.map { (it as? JsonObject)?.getString("email") ?: "" }.filter { it.isNotEmpty() }
            } else {
                logger.warn { "No board found with ID: $boardId" }
                emptyList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve members for board ID: $boardId" }
            emptyList()
        }
    }

    suspend fun deleteBoard(boardId: String): Boolean {
        return try {
            val result = client.removeDocument("boards", JsonObject().put("_id", boardId)).await()
            if (result.removedCount.toInt() == 1) {
                logger.info { "Board deleted successfully with ID: $boardId" }
                true
            } else {
                logger.warn { "No board found to delete with ID: $boardId" }
                false
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete board with ID: $boardId" }
            false
        }
    }

    suspend fun changeMemberRole(boardId: String, memberEmail: String, newRole: String): Boolean {
        try {
            val board = client.findOne("boards", JsonObject().put("_id", boardId), null).await()
            if (board == null) {
                logger.error { "Board not found with ID: $boardId" }
                return false
            }
            val members = board.getJsonArray("members") ?: JsonArray()
            val memberToUpdate = members.firstOrNull { (it as? JsonObject)?.getString("email") == memberEmail }
            if (memberToUpdate == null) {
                logger.warn { "Member not found in board: $memberEmail" }
                return false
            }
            (memberToUpdate as JsonObject).put("role", newRole)
            val updateResult = client.updateCollection(
                "boards",
                JsonObject().put("_id", boardId),
                JsonObject().put("\$set", JsonObject().put("members", members))
            ).await()
            if (updateResult.docModified.toInt() == 1) {
                logger.info { "Member role updated successfully: $memberEmail to $newRole" }
                return true
            } else {
                logger.warn { "Failed to update member role: $memberEmail" }
                return false
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating member role: $memberEmail" }
            return false
        }
    }

    suspend fun getBoardSummary(boardId: String): JsonObject? {
        return try {
            val board = client.findOne("boards", JsonObject().put("_id", boardId), null).await()
            if (board != null) {
                val summary = JsonObject()
                    .put("id", board.getString("_id"))
                    .put("name", board.getString("name"))
                    .put("description", board.getString("description"))
                    .put("createdBy", board.getString("createdBy"))
                    .put("createdAt", board.getString("createdAt"))
                    .put("membersCount", (board.getJsonArray("members") ?: JsonArray()).size())
                // TODO: Later include projects, tasks, etc. in summary
                logger.info { "Board summary retrieved for ID: $boardId" }
                summary
            } else {
                logger.warn { "No board found with ID: $boardId" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve board summary for ID: $boardId" }
            null
        }
    }

    suspend fun getAllBoards(): List<Board> {
        return try {
            val results = client.find("boards", JsonObject()).await()
            if (results.isEmpty()) {
                logger.warn { "No boards found in the database" }
                emptyList()
            } else {
                logger.info { "Found ${results.size} boards in the database" }
                results.map { it.mapTo(Board::class.java) }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve all boards" }
            emptyList()
        }
    }
}