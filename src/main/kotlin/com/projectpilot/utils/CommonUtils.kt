package com.projectpilot.utils

import com.projectpilot.db.MongoProvider
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient

object CommonUtils {
    suspend fun generateUniqueBoardId(): String {
        val client: MongoClient = MongoProvider.getClient()
        var id: String
        var exists: Boolean
        do {
            id = (100000..999999).random().toString()
            exists = client.findOne("boards", JsonObject().put("_id", id), null).await() != null
        } while (exists)
        return id
    }
    suspend fun generateUniqueProjectId(): String {
        val client: MongoClient = MongoProvider.getClient()
        var id: String
        var exists: Boolean
        do {
            id = (100000..999999).random().toString()
            exists = client.findOne("projects", JsonObject().put("_id", id), null).await() != null
        } while (exists)
        return id
    }

    suspend fun generateUniqueTaskId(): String {
        val client: MongoClient = MongoProvider.getClient()
        var id: String
        var exists: Boolean
        do {
            id = (100000..999999).random().toString()
            exists = client.findOne("tasks", JsonObject().put("_id", id), null).await() != null
        } while (exists)
        return id
    }
}
