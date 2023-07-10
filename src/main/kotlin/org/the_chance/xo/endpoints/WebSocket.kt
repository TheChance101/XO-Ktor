package org.the_chance.xo.endpoints

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.the_chance.xo.controller.GameSessionController

fun Routing.xoWebSocket(gameSessionController: GameSessionController) {

    webSocket("/xo-game/{playerName?}/{gameId?}") {

        val gameId = call.parameters["gameId"]
        val playerName = call.parameters["playerName"]

        if (gameId.isNullOrEmpty() && playerName.isNullOrEmpty()) {
            gameSessionController.newGame(this)
        } else {
            gameSessionController.joinGame(gameId, playerName, this)
        }
    }
}


