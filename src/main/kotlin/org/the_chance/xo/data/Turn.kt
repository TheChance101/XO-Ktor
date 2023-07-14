package org.the_chance.xo.data

import kotlinx.serialization.Serializable

@Serializable
data class Turn(val symbol: Char, val x: Int, val y: Int)