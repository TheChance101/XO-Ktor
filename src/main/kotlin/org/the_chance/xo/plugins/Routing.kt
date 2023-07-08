package org.the_chance.xo.plugins

import org.the_chance.xo.endpoints.testRoutes
import io.ktor.server.routing.*
import io.ktor.server.application.*

fun Application.configureRouting(
) {
    routing {
        testRoutes()
    }
}
