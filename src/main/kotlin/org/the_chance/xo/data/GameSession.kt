package org.the_chance.xo.data

import io.ktor.websocket.WebSocketSession

data class GameSession (
    val gameId : String,
    val playerName : String,
    val session : WebSocketSession
)