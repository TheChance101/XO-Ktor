package org.the_chance.xo.utils

import io.ktor.server.websocket.*
import io.ktor.websocket.*

suspend fun DefaultWebSocketServerSession.closeSession(message : String, reason: String) {
    send(message)
    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, reason))
}