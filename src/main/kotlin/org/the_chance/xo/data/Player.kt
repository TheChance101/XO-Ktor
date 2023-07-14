package org.the_chance.xo.data

import java.text.DateFormatSymbols

data class Player(
    val id : Int,
    val name : String,
    val symbol : Char,
    val sendMessageTo : Int
)