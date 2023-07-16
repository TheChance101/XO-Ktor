package org.the_chance.xo.controller


import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.the_chance.xo.controller.utils.getWinningSymbol
import org.the_chance.xo.controller.utils.isBoardFull
import org.the_chance.xo.controller.utils.isPositionTaken
import org.the_chance.xo.controller.utils.updateGameBoard
import org.the_chance.xo.data.Game
import org.the_chance.xo.data.Player
import org.the_chance.xo.data.Turn
import org.the_chance.xo.utils.generateUUID
import java.util.concurrent.ConcurrentHashMap

class GameController {

    private val gameSessions: ConcurrentHashMap<String, MutableList<Game>> = ConcurrentHashMap()
    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun connectPlayer(gameId: String, playerName: String, session: WebSocketSession) {

        if (gameId.isEmpty()) {
            // new player create game
            val newBoard = Array(3) { Array(3) { ' ' } }
            val gameSession = newGame(playerName, session, gameBoard = newBoard)

            println(
                "\nPlayer ${gameSession.player.id} : ${gameSession.player.name} with symbol " +
                        "${gameSession.player.symbol} created new board ${newBoard.hashCode()}\n"
            )

            broadcast(gameSession.player, gameId = gameSession.gameId)

        } else {
            // player has a gameId
            val gameSession = joinGame(gameId, playerName, session)

            println(
                "\nPlayer ${gameSession?.player?.id} : ${gameSession?.player?.name} with symbol" +
                        " ${gameSession?.player?.symbol}\n"
            )

            gameSession?.let {
                broadcast(gameSession.player, gameId = gameSession.gameId)
            }
        }
    }

    private suspend fun broadcast(player: Player, gameId: String) {
        try {

            val gameSessionList = gameSessions[gameId]

            player.session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {

                    // region decode Json
                    val turnJson = frame.readText()
                    val receivedTurn = Json.decodeFromString<Turn>(turnJson)
                    // endregion

                    // region validate and update board
                    val gameBoard = gameSessionList?.get(0)?.gameBoard

                    gameBoard?.let {
                        val x = receivedTurn.x
                        val y = receivedTurn.y
                        if (isPositionTaken(it, x, y)) {
                            player.session.send("Position ($x, $y) is already taken. Try again.")
                            return@consumeEach
                        }
                    }

                    updateGameBoard(gameBoard, player, receivedTurn, gameScope)
                    // endregion

                    // region check winner player and send position
                    gameScope.launch {
                        winnerPlayer(
                            playerX = gameSessionList?.get(0)?.player!!,
                            playerO = gameSessionList[1].player,
                            gameBoard = gameBoard!!
                        )
                    }

                    sendToAnotherPlayer(receivedTurn, player.symbol, gameSessionList)
                    //endregion

                }
            }
        } catch (e: Exception) {
            player.session.close(CloseReason(PLAYER_NOT_CONNECTED, "Player 2 is not connected"))
        } finally {
            leaveGameSession(gameId)
            gameScope.cancel()
        }
    }

    private suspend fun sendToAnotherPlayer(turn: Turn, symbol: Char, gameSessionList: MutableList<Game>?) {
        if (symbol == 'X') {
            gameSessionList?.get(1)?.player?.session?.send("${turn.x},${turn.y}")
        } else {
            gameSessionList?.get(0)?.player?.session?.send("${turn.x},${turn.y}")
        }
    }

    private suspend fun winnerPlayer(playerX: Player, playerO: Player, gameBoard: Array<Array<Char>>) {
        val winningSymbol = getWinningSymbol(gameBoard)

        if (winningSymbol != null) {
            if (winningSymbol == 'O') {
                playerO.session.close(CloseReason(WIN_CODE, "Congratulations! You won!"))
                playerX.session.close(CloseReason(LOST_CODE, "You lost!"))
            } else {
                playerO.session.close(CloseReason(LOST_CODE, "You lost!"))
                playerX.session.close(CloseReason(WIN_CODE, "Congratulations! You won!"))
            }
        } else if (isBoardFull(gameBoard)) {
            playerO.session.close(CloseReason(DRAW_CODE, "It's a tie!"))
            playerX.session.close(CloseReason(DRAW_CODE, "It's a tie!"))
        }
    }

    //region handel player session

    private fun createSession(
        gameId: String,
        playerName: String,
        session: WebSocketSession,
        gameBoard: Array<Array<Char>>? = null
    ): Game {
        return Game(
            gameId = gameId,
            player = Player(id = 0, name = playerName, symbol = 'X', session = session),
            gameBoard = gameBoard
        )
    }

    private suspend fun newGame(
        playerName: String,
        session: WebSocketSession,
        gameBoard: Array<Array<Char>>
    ): Game {
        val newGameId = generateUUID()
        val gameSession = createSession(newGameId, playerName, session, gameBoard)
        session.send(newGameId)
        gameSessions[newGameId] = mutableListOf(gameSession)
        return gameSession
    }

    private suspend fun joinGame(gameId: String, playerName: String, session: WebSocketSession): Game? {

        if (!isValidGameSession(gameId)) {
            session.close(CloseReason(GAME_ID_NOT_VALID, "Game Id not valid"))
            return null
        }


        val gameSessionList = gameSessions[gameId]

        if ((gameSessionList?.size ?: 0) < MAX_PLAYERS) {
            val gameSession = createSession(gameId, playerName, session)
                .copy(player = Player(id = 1, name = playerName, symbol = 'O', session = session))

            gameSessionList?.add(gameSession)
            return gameSession
        } else {
            session.close(CloseReason(FULL_ROOM, "Room is Full"))
        }
        return null
    }

    private fun leaveGameSession(gameId: String) {
        gameSessions.remove(gameId)
    }

    private fun isValidGameSession(gameId: String): Boolean {
        return gameSessions.containsKey(gameId)
    }

    //endregion

    companion object {
        private const val MAX_PLAYERS = 2
        private const val FULL_ROOM: Short = 7
        private const val GAME_ID_NOT_VALID: Short = 8
        private const val PLAYER_NOT_CONNECTED: Short = 9
        private const val WIN_CODE: Short = 10
        private const val LOST_CODE: Short = 11
        private const val DRAW_CODE: Short = 12
    }

}