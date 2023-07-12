package org.the_chance.xo.endpoints

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.the_chance.xo.controller.GameSessionController
import org.the_chance.xo.data.MakeTurn

fun Routing.xoWebSocket(game: GameSessionController) {

    webSocket("/xo-game/{playerName}/{gameId?}") {

        val gameId = call.parameters["gameId"]
        val playerName = call.parameters["playerName"]?.trim().orEmpty()

        val player: Char? = if (gameId.isNullOrEmpty()) {
            game.newGame(playerName, this)
        } else {
            game.joinGame(gameId, playerName, this)
        }
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val action = extractAction(frame.readText())
                    game.finishTurn(player!!, action.x, action.y)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }


    }
}

private fun extractAction(message: String): MakeTurn {
    // make_turn#{...}
    val type = message.substringBefore("#")
    val body = message.substringAfter("#")
    return if (type == "make_turn") {
        Json.decodeFromString(body)
    } else MakeTurn(-1, -1)
}


