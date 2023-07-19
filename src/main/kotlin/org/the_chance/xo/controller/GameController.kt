package org.the_chance.xo.controller


import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
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
            val newBoard = Array(3) { Array(3) { ' ' } }
            val game = newGame(playerName, session, gameBoard = newBoard)
            game.player1?.let { broadcast(it, gameId = game.gameId) }
        } else {
            val game = joinGame(gameId, playerName, session)
            game?.player2?.let { broadcast(it, gameId = game.gameId) }
        }
    }

    private suspend fun broadcast(player: Player, gameId: String) {
        try {
            player.session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val turnJson = frame.readText()
                    val receivedTurn = Json.decodeFromString<Turn>(turnJson)
                    val game = games[gameId] ?: return@consumeEach

                    checkPositionAndUpdateGameBoard(game, receivedTurn, player) ?: return@consumeEach
                    checkTheWinner(game)
                    sendToAnotherPlayer(receivedTurn, player.symbol, game)
                    switchTurnPlayer(game)
                }
            }
        } catch (e: Exception) {
            player.session.close(CloseReason(PLAYER_NOT_CONNECTED, "Player 2 is not connected"))
        } finally {
            leaveGame(gameId)
            gameScope.cancel()
        }
    }

    private fun switchTurnPlayer(game: Game) {
        games[game.gameId] = game.copy(isFirstPlayerTurn = !game.isFirstPlayerTurn)
    }

    private suspend fun checkTheWinner(game: Game) {
        gameScope.launch {
            if (game.player1 != null && game.player2 != null && game.gameBoard != null) {
                winnerPlayer(playerX = game.player1, playerO = game.player2, gameBoard = game.gameBoard)
            }
        }
    }

    private suspend fun checkPositionAndUpdateGameBoard(game: Game, receivedTurn: Turn, player: Player): Unit? {
        game.gameBoard?.let {
            val x = receivedTurn.row
            val y = receivedTurn.column
            if (isPositionTaken(it, x, y)) {
                player.session.send("Position is already taken. Try again.")
                return null
            } else if (!isPlayerTurn(player.symbol, game)) {
                player.session.send("Not your turn")
                return null
            }
        }
        updateGameBoard(game.gameBoard, player, receivedTurn, gameScope)
        return Unit
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
//        val game = GameState(win = "", draw = true, lose = "")
//        val jsonText = Json.encodeToString(game)
        playerO.session.send("draw")
        playerX.session.send("draw")
        playerO.session.close(CloseReason(DRAW_CODE, "End Game"))
        playerX.session.close(CloseReason(DRAW_CODE, "End Game"))
    }

    private suspend fun notifyWinAndEndGame(winner: Player, loser: Player) {
        val game = GameState(win = winner.symbol.toString(), draw = false, lose = loser.symbol.toString())
//        val jsonText = Json.encodeToString(game)
        winner.session.send("win#${game.win}")
        loser.session.send("win#${game.win}")
        loser.session.close(CloseReason(LOST_CODE, "End Game"))
        winner.session.close(CloseReason(WIN_CODE, "End Game"))
    }

    //region handel connect player

    private suspend fun newGame(playerName: String, session: WebSocketSession, gameBoard: Array<Array<Char>>): Game {
        val newGameId = generateUUID()
        val game = Game(
                newGameId, player1 = Player(id = 0, name = playerName, symbol = 'X', session = session),
                isFirstPlayerTurn = true, gameBoard = gameBoard
        )
        session.send(newGameId)
        games[newGameId] = game
        return game
    }

    private suspend fun joinGame(gameId: String, playerName: String, session: WebSocketSession): Game? {

        if (!isValidGameId(gameId)) {
            session.close(CloseReason(GAME_ID_NOT_VALID, "Game Id not valid"))
            return null
        }

        val game = games[gameId]
        if (game?.player2 != null) {
            session.close(CloseReason(FULL_ROOM, "Room is Full"))
        } else if (game != null) {
            val updateGame = game.copy(player2 = Player(id = 1, name = playerName, symbol = 'O', session = session))
            games[gameId] = updateGame
            updateGame.player1?.session?.send("players : #${updateGame.player2?.name}")
            updateGame.player2?.session?.send("players : #${updateGame.player1?.name}")
            return updateGame
        }
        return null
    }

    private fun leaveGame(gameId: String) {
        games.remove(gameId)
    }

    private fun isValidGameId(gameId: String): Boolean {
        return games.containsKey(gameId)
    }

    //endregion

    companion object {
        private const val FULL_ROOM: Short = 7
        private const val GAME_ID_NOT_VALID: Short = 8
        private const val PLAYER_NOT_CONNECTED: Short = 9
        private const val WIN_CODE: Short = 10
        private const val LOST_CODE: Short = 11
        private const val DRAW_CODE: Short = 12
    }

}

