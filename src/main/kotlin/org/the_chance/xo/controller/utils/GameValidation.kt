package org.the_chance.xo.controller.utils




fun isBoardFull(gameBoard: Array<Array<Char>>): Boolean {
    for (row in gameBoard) {
        for (cell in row) {
            if (cell == ' ') {
                return false
            }
        }
    }
    return true
}

fun isPositionTaken(gameBoard: Array<Array<Char>>, x: Int, y: Int): Boolean {
    return gameBoard[x][y] != ' '
}

fun getWinningSymbol(gameBoard: Array<Array<Char>>): Char? {
    // Check rows
    for (row in gameBoard) {
        if (row[0] != ' ' && row[0] == row[1] && row[1] == row[2]) {
            return row[0]
        }
    }

    // Check columns
    for (col in 0 until 3) {
        if (gameBoard[0][col] != ' ' && gameBoard[0][col] == gameBoard[1][col] && gameBoard[1][col] == gameBoard[2][col]) {
            return gameBoard[0][col]
        }
    }

    // Check diagonals
    if (gameBoard[0][0] != ' ' && gameBoard[0][0] == gameBoard[1][1] && gameBoard[1][1] == gameBoard[2][2]) {
        return gameBoard[0][0]
    }
    if (gameBoard[0][2] != ' ' && gameBoard[0][2] == gameBoard[1][1] && gameBoard[1][1] == gameBoard[2][0]) {
        return gameBoard[0][2]
    }

    return null
}

fun print2DArray(array: Array<Array<Char>>) {
    for (row in array) {
        for (cell in row) {
            print("$cell ")
        }
        println()
    }
}

fun clearBoard(gameBoard: Array<Array<Char>>) {
    for (i in gameBoard.indices) {
        for (j in gameBoard[i].indices) {
            gameBoard[i][j] = ' '
        }
    }
}