package org.the_chance.xo.data

import io.ktor.websocket.*

data class GameSession(
    val gameId: String,
    val playerName: String,
    val playerSymbol: Char,
    val session: WebSocketSession
)