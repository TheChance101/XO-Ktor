package org.the_chance.xo

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.the_chance.xo.controller.GameController
import org.the_chance.xo.plugins.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {

    val gameController = GameController()

    configureSockets()
    configureSerialization()
    configureMonitoring()
    configureRouting(gameController)
}
