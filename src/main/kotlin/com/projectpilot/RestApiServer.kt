package com.projectpilot

import com.projectpilot.db.MongoProvider
import com.projectpilot.routes.authRoutes
import com.projectpilot.utils.Config
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.ext.web.handler.BodyHandler
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class RestApiServer: CoroutineVerticle() {
    override suspend fun start() {
        MongoProvider.init(vertx)

        val router: Router = Router.router(vertx)
        router.route().handler(BodyHandler.create())

        router.get("/").handler { ctx ->
            ctx.response()
                .putHeader("content-type", "text/plain")
                .end("Welcome to ProjectPilot API!")
            logger.info( "Received request on /" )
        }

        authRoutes(vertx, router)

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(Config.server_port)
            .onSuccess { println("Server is listening on port ${Config.server_port}") }
            .onFailure { it.printStackTrace() }
        logger.info ({ "RestApiServer started successfully." })
    }
}

fun main() {
    val vertx: Vertx = Vertx.vertx()
    vertx.deployVerticle(RestApiServer())
}