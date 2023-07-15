package org.the_chance.xo.controller


import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.the_chance.xo.data.GameSession
import org.the_chance.xo.data.Player
import org.the_chance.xo.data.Turn
import org.the_chance.xo.data.empty2DArray
import org.the_chance.xo.utils.closeSession
import org.the_chance.xo.utils.generateUUID
import java.util.concurrent.ConcurrentHashMap

class GameController {

    private val gameSessions: ConcurrentHashMap<String, MutableList<GameSession>> = ConcurrentHashMap()
    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val MAX_PLAYERS = 2
    }

    suspend fun connectPlayer(gameId: String, playerName: String, session: WebSocketSession) {

        if (gameId.isEmpty()) {
            // new player create game
            val newBoard = Array(3) { Array(3) { ' ' } }
            val gameSession = newGame(playerName, session, gameBoard = newBoard)
            println("\nplayer 1 created new board ${newBoard.hashCode()}\n")
            val player = Player(id = 0, name = playerName, symbol = 'X', sendMessageTo = 1)
            broadcast(player, gameId = gameSession.gameId, session = gameSession)

        } else {
            // player has a gameId
            val gameSession = joinGame(gameId, playerName, session)

            gameSession?.let {
                val player = Player(id = 1, name = playerName, symbol = 'O', sendMessageTo = 0)
                broadcast(player, gameId = gameSession.gameId, session = it)
            }
        }
    }

    private suspend fun broadcast(
        player: Player,
        gameId: String,
        session: GameSession
    ) {
        try {
            val gameSessionList = gameSessions[gameId]

            session.session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {

                    val turnJson = frame.readText()
                    val receivedTurn = Json.decodeFromString<Turn>(turnJson)
                    val x = receivedTurn.x
                    val y = receivedTurn.y

                    val receiver = gameSessionList?.get(player.sendMessageTo)
                    println("$$$$$$$$$$$$$$${receiver?.playerName}--------------")
                    val sender = if (player.sendMessageTo == 1) gameSessionList?.get(0) else gameSessionList?.get(1)

                    val gameBoard = gameSessionList?.get(0)?.gameBoard
                    println("------------------$gameBoard--------------")

                    println("player ${receiver?.playerName} playing on board ${gameBoard.hashCode()}")
                    println("player ${sender?.playerName} playing on board ${gameBoard.hashCode()}")

                    gameBoard?.let {
                        if (isPositionTaken(it, x, y)) {
                            session.session.send("Position ($x, $y) is already taken. Try again.")
                            return@consumeEach
                        }
                    }

                    updateGameBoard(gameBoard, player, receivedTurn, receiver, sender)
                }

            }
        } catch (e: Exception) {
            session.session.close(CloseReason(12,"Player 2 is not connected"))
        } finally {
            leaveGameSession(gameId, session)
            println("mustafa ${gameSessions[gameId]?.size}")
        }
    }

    private fun updateGameBoard(
        gameBoard: Array<Array<Char>>?,
        player: Player,
        receivedTurn: Turn,
        receiver: GameSession?,
        sender: GameSession?
    ) {
        gameScope.launch {
            gameBoard?.let {
                gameBoard[receivedTurn.x][receivedTurn.y] = player.symbol

                print2DArray(gameBoard)

                val winningSymbol = getWinningSymbol(gameBoard)

                if (winningSymbol != null) {
                    if (winningSymbol == 'O') {
                        receiver?.session?.send("Congratulations! You won!")
                        sender?.session?.send("You lost!")
                        receiver?.session?.closeSession("End Game", "End Game")
                        sender?.session?.closeSession("End Game", "End Game")
                    } else {
                        receiver?.session?.send("You lost!")
                        sender?.session?.send("Congratulations! You won!")
                        receiver?.session?.closeSession("End Game", "End Game")
                        sender?.session?.closeSession("End Game", "End Game")
                    }

                } else if (isBoardFull(gameBoard)) {
                    receiver?.session?.send("It's a tie!")
                    sender?.session?.send("It's a tie!")
                } else {
                    receiver?.session?.send("${receivedTurn.x},${receivedTurn.y}")
                }
            }
        }
    }

    private fun print2DArray(array: Array<Array<Char>>) {
        for (row in array) {
            for (cell in row) {
                print("$cell ")
            }
            println()
        }
    }

    private fun clearBoard(gameBoard: Array<Array<Char>>) {
        for (i in gameBoard.indices) {
            for (j in gameBoard[i].indices) {
                gameBoard[i][j] = ' '
            }
        }
    }

    private fun isPositionTaken(gameBoard: Array<Array<Char>>, x: Int, y: Int): Boolean {
        return gameBoard[x][y] != ' '
    }

    // when create session you create communicate between player and server
    private fun createSession(
        gameId: String,
        playerName: String,
        session: WebSocketSession,
        gameBoard: Array<Array<Char>>? = null
    ): GameSession {
        return GameSession(
            gameId = gameId,
            playerName = playerName,
            playerSymbol = 'X',
            session = session,
            gameBoard = gameBoard
        )
    }

    private suspend fun newGame(
        playerName: String,
        session: WebSocketSession,
        gameBoard: Array<Array<Char>>
    ): GameSession {
        val newGameId = generateUUID()
        println(newGameId)
        val gameSession = createSession(newGameId, playerName, session, gameBoard)
        session.send(newGameId)
        gameSessions[newGameId] = mutableListOf(gameSession)
        return gameSession
    }

    private suspend fun joinGame(gameId: String, playerName: String, session: WebSocketSession): GameSession? {

        if (!isValidGameSession(gameId)) {
            session.close(CloseReason(CloseReason.Codes.NORMAL, "Game Id not valid"))
            return null
        }

        // get players channel
        val gamePlayersChannel = gameSessions[gameId]

        if ((gamePlayersChannel?.size ?: 0) < MAX_PLAYERS) {
            val gameSession = createSession(gameId, playerName, session).copy(playerSymbol = 'O')
            gamePlayersChannel?.add(gameSession)
            return gameSession
        } else {
            session.close(CloseReason(CloseReason.Codes.NORMAL, "Room is Full"))
        }
        return null
    }

    private fun leaveGameSession(gameId: String, game: GameSession) {
        val session = gameSessions[gameId]
        session?.remove(game)
        if (session?.isEmpty() == true) {
            gameSessions.remove(gameId)
        }
    }

    private fun isValidGameSession(gameId: String): Boolean {
        return gameSessions.containsKey(gameId)
    }

    private fun isBoardFull(gameBoard: Array<Array<Char>>): Boolean {
        for (row in gameBoard) {
            for (cell in row) {
                if (cell == ' ') {
                    return false
                }
            }
        }
        return true
    }

    private fun getWinningSymbol(gameBoard: Array<Array<Char>>): Char? {
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
}