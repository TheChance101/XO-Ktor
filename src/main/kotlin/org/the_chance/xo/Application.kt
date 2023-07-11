package org.the_chance.xo

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.the_chance.xo.models.TicTacToeGame
import org.the_chance.xo.plugins.configureMonitoring
import org.the_chance.xo.plugins.configureRouting
import org.the_chance.xo.plugins.configureSerialization
import org.the_chance.xo.plugins.configureSockets

fun main() {
    embeddedServer(Netty, port = 8082, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val ticTacToeGame = TicTacToeGame()

    configureSerialization()
    configureMonitoring()
    configureSockets()
    configureRouting(ticTacToeGame)
}
