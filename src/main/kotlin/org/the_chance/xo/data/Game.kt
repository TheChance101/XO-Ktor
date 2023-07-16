package org.the_chance.xo.data

data class Game(
    val gameId: String,
    val player1: Player? = null,
    val player2: Player? = null,
    val isFirstPlayerTurn: Boolean = true,
    val gameBoard: Array<Array<Char>>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Game

        if (gameId != other.gameId) return false
        if (player1 != other.player1) return false
        if (player2 != other.player2) return false
        if (gameBoard != null) {
            if (other.gameBoard == null) return false
            if (!gameBoard.contentDeepEquals(other.gameBoard)) return false
        } else if (other.gameBoard != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gameId.hashCode()
        result = 31 * result + player1.hashCode()
        result = 31 * result + player2.hashCode()
        result = 31 * result + (gameBoard?.contentDeepHashCode() ?: 0)
        return result
    }

}