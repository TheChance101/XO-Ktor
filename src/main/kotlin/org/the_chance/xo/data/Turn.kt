package org.the_chance.xo.data

import kotlinx.serialization.Serializable

@Serializable
data class Turn(val row: Int, val column: Int)