package org.the_chance.xo.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.the_chance.xo.controller.GameController
import org.the_chance.xo.endpoints.xoWebSocket

fun Application.configureRouting(
    gameController: GameController
) {
    routing {
        xoWebSocket(gameController)
    }
}
