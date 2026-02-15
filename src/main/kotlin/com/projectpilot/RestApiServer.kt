package com.projectpilot

import com.projectpilot.db.MongoProvider
import com.projectpilot.db.RedisProvider
import com.projectpilot.routes.authRoutes
import com.projectpilot.routes.boardRoutes
import com.projectpilot.routes.projectRoutes
import com.projectpilot.routes.taskRoutes
import com.projectpilot.utils.Config
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.ext.web.handler.BodyHandler

private val logger = KotlinLogging.logger {}

class RestApiServer: CoroutineVerticle() {
    override suspend fun start() {
        MongoProvider.init(vertx)
        RedisProvider.init(vertx)
        logger.info { "Initializing ProjectPilot Rest API Server..." }

        val router: Router = Router.router(vertx)
        router.route().handler(BodyHandler.create())
        logger.info { "Router initialized with BodyHandler" }

        router.get("/").handler { ctx ->
            ctx.response()
                .putHeader("content-type", "text/plain")
                .end("Welcome to ProjectPilot API!")
            logger.info { "Received request on /" }
        }

        authRoutes(vertx, router)
        boardRoutes(vertx, router)
        projectRoutes(vertx, router)
        taskRoutes(vertx, router)
        logger.info { "Routes registered" }

        val server = vertx.createHttpServer()
        server.requestHandler(router)
            .listen(Config.server_port)
            .onSuccess {
                logger.info { "🚀 Server started on port 8080" }
            }
            .onFailure {
                logger.error(it) { "Failed to start server" }
            }
        logger.info { "RestApiServer started successfully." }

        // ✅ Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("🛑 Shutdown signal received. Closing resources...")

            try {
                server.close()
                logger.info("HTTP server closed")
            } catch (e: Exception) {
                logger.error("Error closing HTTP server", e)
            }

            try {
                MongoProvider.close()
                logger.info("Mongo client closed")
            } catch (e: Exception) {
                logger.error("Error closing Mongo client", e)
            }

            try {
                RedisProvider.close()
                logger.info("Redis client closed")
            } catch (e: Exception) {
                logger.error("Error closing Redis client", e)
            }

            try {
                vertx.close()
                logger.info("Vert.x instance closed")
            } catch (e: Exception) {
                logger.error("Error closing Vert.x", e)
            }

            logger.info("✅ Graceful shutdown complete.")
        })
    }
}

fun main() {
    val vertx: Vertx = Vertx.vertx()
    logger.info { "Starting ProjectPilot Rest API Server..." }
    vertx.deployVerticle(RestApiServer())
}