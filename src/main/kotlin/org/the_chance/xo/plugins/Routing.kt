package org.the_chance.xo.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.the_chance.xo.endpoints.playXo
import org.the_chance.xo.models.TicTacToeGame

fun Application.configureRouting(ticTacToeGame: TicTacToeGame) {
    routing {
        playXo(ticTacToeGame)
    }
}
