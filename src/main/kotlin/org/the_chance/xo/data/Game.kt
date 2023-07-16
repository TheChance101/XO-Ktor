package org.the_chance.xo.data

data class Game(
    val gameId: String,
    val player: Player,
    val gameBoard: Array<Array<Char>>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Game

        if (gameId != other.gameId) return false
        if (player != other.player) return false
        if (gameBoard != null) {
            if (other.gameBoard == null) return false
            if (!gameBoard.contentDeepEquals(other.gameBoard)) return false
        } else if (other.gameBoard != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gameId.hashCode()
        result = 31 * result + player.hashCode()
        result = 31 * result + (gameBoard?.contentDeepHashCode() ?: 0)
        return result
    }

}