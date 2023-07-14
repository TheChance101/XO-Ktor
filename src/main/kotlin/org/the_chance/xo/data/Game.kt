package org.the_chance.xo.data

import io.ktor.websocket.WebSocketSession
import kotlinx.serialization.Serializable

data class Game(
    val gameId: String,
    val playerName: String,
    val playerSymbol: Char = 'X',
    val boardIsFull: Boolean = false,
    val session: WebSocketSession,
    val winningPlayerName: String = "",
    var nextMove: Char = 'O',
)

data class GameSession(
    val gameId: String,
    val playerName: String,
    val playerSymbol: Char,
    val session: WebSocketSession
)

@Serializable
data class Turn(val x: Int,val y: Int)