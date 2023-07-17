package org.the_chance.xo.controller


import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.the_chance.xo.controller.utils.getWinningSymbol
import org.the_chance.xo.controller.utils.isBoardFull
import org.the_chance.xo.controller.utils.isPositionTaken
import org.the_chance.xo.controller.utils.updateGameBoard
import org.the_chance.xo.data.*
import org.the_chance.xo.utils.generateUUID
import java.util.concurrent.ConcurrentHashMap

class GameController {

    private val games: ConcurrentHashMap<String, Game> = ConcurrentHashMap()
    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun connectPlayer(gameId: String, playerName: String, session: WebSocketSession) {

        if (gameId.isEmpty()) {
            // new player create game
            val newBoard = Array(3) { Array(3) { ' ' } }
            val game = newGame(playerName, session, gameBoard = newBoard)

            println(
                    "\nPlayer ${game.player1?.id} : ${game.player1?.name} with symbol " +
                            "${game.player1?.symbol} created new board ${newBoard.hashCode()}\n"
            )

            game.player1?.let { broadcast(it, gameId = game.gameId) }

        } else {
            // player has a gameId
            val game = joinGame(gameId, playerName, session)

            game?.player2?.let {
                broadcast(it, gameId = game.gameId)
            }
        }
    }

    private suspend fun broadcast(player: Player, gameId: String) {
        try {

            player.session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {

                    // region decode Json
                    val turnJson = frame.readText()
                    val receivedTurn = Json.decodeFromString<Turn>(turnJson)
                    // endregion

                    // region validate and update board
                    val game = games[gameId] ?: return@consumeEach
                    val gameBoard = game.gameBoard

                    gameBoard?.let {
                        val x = receivedTurn.row
                        val y = receivedTurn.column
                        if (isPositionTaken(it, x, y)) {
                            player.session.send("Position ($x, $y) is already taken. Try again.")
                            return@consumeEach
                        } else if (!isPlayerTurn(player.symbol, game)) {
                            player.session.send("Not your turn")
                            return@consumeEach
                        }
                    }

                    updateGameBoard(gameBoard, player, receivedTurn, gameScope)

                    // endregion

                    // region check winner player and send position
                    gameScope.launch {
                        if (game.player1 != null && game.player2 != null && gameBoard != null) {
                            winnerPlayer(
                                    playerX = game.player1,
                                    playerO = game.player2,
                                    gameBoard = gameBoard
                            )
                        }
                    }

                    sendToAnotherPlayer(receivedTurn, player.symbol, game)

                    games[gameId] = game.copy(isFirstPlayerTurn = !game.isFirstPlayerTurn)

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

    private fun isPlayerTurn(playerSymbol: Char, game: Game): Boolean {
        if (playerSymbol == 'X' && game.isFirstPlayerTurn) {
            return true
        }
        return playerSymbol == 'O' && !game.isFirstPlayerTurn
    }

    private suspend fun sendToAnotherPlayer(turn: Turn, symbol: Char, game: Game?) {
        val gameBoard = GameBoard(playTurn = symbol.toString(), position = turn)
        if (symbol == 'X') {
            val jsonText = Json.encodeToString(gameBoard.copy(playTurn = "O"))
            game?.player2?.session?.send(jsonText)
        } else {
            val jsonText = Json.encodeToString(gameBoard.copy(playTurn = "X"))
            game?.player1?.session?.send(jsonText)
        }
    }

    private suspend fun winnerPlayer(playerX: Player, playerO: Player, gameBoard: Array<Array<Char>>) {
        val winningSymbol = getWinningSymbol(gameBoard)
        if (winningSymbol != null) {
            handleGameOutcome(winningSymbol, playerX, playerO)
        } else if (isBoardFull(gameBoard)) {
            notifyDrawAndEndGame(playerX, playerO)
        }
    }

    private suspend fun handleGameOutcome(winningSymbol: Char, playerX: Player, playerO: Player) {
        if (winningSymbol == 'O') {
            notifyWinAndEndGame(winner = playerO, loser = playerX)
        } else {
            notifyWinAndEndGame(winner = playerX, loser = playerO)
        }
    }

    private suspend fun notifyDrawAndEndGame(playerX: Player, playerO: Player) {
        val game = GameState(win = "", draw = true, lose = "")
        val jsonText = Json.encodeToString(game)
        playerO.session.send(jsonText)
        playerX.session.send(jsonText)
        playerO.session.close(CloseReason(DRAW_CODE, "End Game"))
        playerX.session.close(CloseReason(DRAW_CODE, "End Game"))
    }

    private suspend fun notifyWinAndEndGame(winner: Player, loser: Player) {
        val game = GameState(win = winner.symbol.toString(), draw = false, lose = loser.symbol.toString())
        val jsonText = Json.encodeToString(game)
        winner.session.send(jsonText)
        loser.session.send(jsonText)
        loser.session.close(CloseReason(LOST_CODE, "End Game"))
        winner.session.close(CloseReason(WIN_CODE, "End Game"))

    }

    //region handel player session

    private suspend fun newGame(
            playerName: String,
            session: WebSocketSession,
            gameBoard: Array<Array<Char>>
    ): Game {
        val newGameId = generateUUID()
        val game = Game(
                newGameId,
                player1 = Player(id = 0, name = playerName, symbol = 'X', session = session),
                isFirstPlayerTurn = true,
                gameBoard = gameBoard
        )
        session.send(newGameId)
        games[newGameId] = game
        return game
    }

    private suspend fun joinGame(gameId: String, playerName: String, session: WebSocketSession): Game? {

        if (!isValidGameSession(gameId)) {
            session.close(CloseReason(GAME_ID_NOT_VALID, "Game Id not valid"))
            return null
        }

        val game = games[gameId]
        if (game?.player2 != null) {
            session.close(CloseReason(FULL_ROOM, "Room is Full"))
        } else if (game != null) {

            val updateGame = game.copy(player2 = Player(id = 1, name = playerName, symbol = 'O', session = session))
            games[gameId] = updateGame
            game.player1?.session?.send("Your Friend Joined the game")
            return updateGame
        }
        return null
    }

    private fun leaveGameSession(gameId: String) {
        games.remove(gameId)
    }

    private fun isValidGameSession(gameId: String): Boolean {
        return games.containsKey(gameId)
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

