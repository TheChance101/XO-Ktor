package org.the_chance.xo.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.the_chance.xo.controller.GameSessionController
import org.the_chance.xo.endpoints.xoWebSocket

fun Application.configureRouting(
    gameSessionController: GameSessionController
) {
    routing {
        xoWebSocket(gameSessionController)
    }
}
