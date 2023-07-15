package org.the_chance.xo.data

import io.ktor.websocket.*


val empty2DArray: Array<Array<Char>> = arrayOf(
    arrayOf(' ', ' ', ' '),
    arrayOf(' ', ' ', ' '),
    arrayOf(' ', ' ', ' '),
)

data class GameSession(
    val gameId: String,
    val playerName: String,
    val playerSymbol: Char,
    val session: WebSocketSession,
    val gameBoard: Array<Array<Char>>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameSession

        if (gameId != other.gameId) return false
        if (playerName != other.playerName) return false
        if (playerSymbol != other.playerSymbol) return false
        if (session != other.session) return false
        if (gameBoard != null) {
            if (other.gameBoard == null) return false
            if (!gameBoard.contentDeepEquals(other.gameBoard)) return false
        } else if (other.gameBoard != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gameId.hashCode()
        result = 31 * result + playerName.hashCode()
        result = 31 * result + playerSymbol.hashCode()
        result = 31 * result + session.hashCode()
        result = 31 * result + (gameBoard?.contentDeepHashCode() ?: 0)
        return result
    }
}