package org.the_chance.xo.data

import kotlinx.serialization.Serializable

@Serializable
data class GameBoard(
        val position: Turn,
        val playTurn: String ,
)

