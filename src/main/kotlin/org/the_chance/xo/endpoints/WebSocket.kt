package org.the_chance.xo.endpoints

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.the_chance.xo.controller.GameController

fun Routing.xoWebSocket(gameController: GameController) {

    webSocket("/xo-game/{playerName}/{gameId?}") {
        val gameId = call.parameters["gameId"]?.trim().orEmpty()
        val playerName = call.parameters["playerName"]?.trim().orEmpty()
        gameController.connectPlayer(gameId, playerName, this)
    }
}


