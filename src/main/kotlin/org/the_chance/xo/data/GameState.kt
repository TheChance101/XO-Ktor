package org.the_chance.xo.data

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
        val win: String,
        val draw: Boolean,
        val lose: String,
)
