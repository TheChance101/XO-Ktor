package org.the_chance.xo.data

import io.ktor.websocket.*


data class Player(
    val id : Int,
    val name : String,
    val symbol : Char,
    val session: WebSocketSession
)