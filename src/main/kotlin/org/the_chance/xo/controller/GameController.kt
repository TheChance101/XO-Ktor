package org.the_chance.xo.controller


import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.the_chance.xo.data.GameSession
import org.the_chance.xo.data.Turn
import org.the_chance.xo.utils.generateUUID
import java.util.concurrent.ConcurrentHashMap

class GameController {

    private val gameSessions: ConcurrentHashMap<String, MutableList<GameSession>> = ConcurrentHashMap()
    private val gameBoard: Array<Array<Char>> = emptyArray()

    companion object {
        private const val MAX_PLAYERS = 2
    }

    suspend fun connectPlayer(gameId: String, playerName: String, session: WebSocketSession) {
        if (gameId.isEmpty()) {
            // new player create game
            val gameSession = newGame(playerName, session)

            broadcast(position = 1, gameId = gameSession.gameId, session = gameSession)

        } else {
            // player has a gameId
            val gameSession = joinGame(gameId, playerName, session)

            gameSession?.let {
                broadcast(position = 0, gameId = gameSession.gameId, session = it)
            }
        }
    }

    private suspend fun broadcast(position: Int, gameId: String, session: GameSession) {
        try {
            val gameSessionList = gameSessions[gameId]
            session.session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val turnJson = frame.readText()
                    val receivedTurn = Json.decodeFromString<Turn>(turnJson)
                    val receiver = gameSessionList?.get(position)
                    receiver?.session?.send("${receivedTurn.x},${receivedTurn.y}")
                }
            }
        } catch (e: Exception) {
            // todo should be the second player
            session.session.send(e.message.toString())
        } finally {
            leaveGameSession(gameId, session)
        }
    }


    // when create session you create communicate between player and server
    private fun createSession(gameId: String, playerName: String, session: WebSocketSession): GameSession {
        return GameSession(gameId = gameId, playerName = playerName, playerSymbol = 'X', session = session)
    }

    private fun newGame(playerName: String, session: WebSocketSession): GameSession {
        val newGameId = generateUUID()
        println(newGameId)
        val gameSession = createSession(newGameId, playerName, session)
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
}