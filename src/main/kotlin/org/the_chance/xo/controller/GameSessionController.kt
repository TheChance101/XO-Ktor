package org.the_chance.xo.controller


import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import org.the_chance.xo.data.GameSession
import org.the_chance.xo.utils.closeSession
import org.the_chance.xo.utils.generateUUID
import java.util.concurrent.ConcurrentHashMap

class GameSessionController {

    private val gameSessions: ConcurrentHashMap<String, MutableList<GameSession>> = ConcurrentHashMap()

    companion object {
        private const val MAX_PLAYERS = 2
    }

    private fun createGameSession(
        playerNameParams: String?,
        defaultWebSocketServerSession: DefaultWebSocketServerSession
    ): GameSession {
        val gameSessionId = generateUUID()
        val playerName = "user-$playerNameParams"
        gameSessions[gameSessionId] = mutableListOf()
        return GameSession(gameSessionId, playerName, defaultWebSocketServerSession)
    }


    private fun joinGameSession(sessionId: String, gameSession: GameSession): Boolean {
        val session = gameSessions[sessionId]

        if ((session?.size ?: 0) < MAX_PLAYERS) {
            session?.add(gameSession)
            return true
        }

        return false
    }


    private fun leaveGameSession(sessionId: String, gameSession: GameSession) {
        val session = gameSessions[sessionId]
        session?.remove(gameSession)
        if (session?.isEmpty() == true) {
            gameSessions.remove(sessionId)
        }
    }


    private fun isValidGameSession(gameSessionId: String): Boolean {
        return gameSessions.containsKey(gameSessionId)
    }


    private suspend fun handleGameSessionCommunication(session: DefaultWebSocketServerSession, gameSessionId: String) {
        try {
            val sessionList = gameSessions[gameSessionId]
            println("mustafa $sessionList")
            session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    sessionList?.forEach {
                        it.session.send(message)
                    }
                }
            }
        } catch (ex: ClosedReceiveChannelException) {
            session.send(ex.message.toString())
        }
    }

    suspend fun newGame(playerNameParams: String?, defaultWebSocketServerSession: DefaultWebSocketServerSession) {
        val gameSession = createGameSession(playerNameParams, defaultWebSocketServerSession)
        val gameSessionId = gameSession.gameId
        val playerName = gameSession.playerName

        defaultWebSocketServerSession.send("Created game session with ID: $gameSessionId, Player Name: $playerName")

        val joined = joinGameSession(gameSessionId, gameSession)

        if (joined) {
            handleGameSessionCommunication(defaultWebSocketServerSession, gameSessionId)
            leaveGameSession(sessionId = gameSessionId, gameSession = gameSession)
        } else {
            defaultWebSocketServerSession.closeSession(
                message = "Room is full. You cannot join at the moment.",
                reason = "Room is full."
            )
        }
    }


    suspend fun joinGame(
        gameId: String?,
        playerName: String?,
        defaultWebSocketServerSession: DefaultWebSocketServerSession
    ) {
        if (gameId.isNullOrEmpty() || playerName.isNullOrEmpty()) {
            defaultWebSocketServerSession.close(CloseReason(CloseReason.Codes.NORMAL, "Invalid request."))
            return
        }

        if (!isValidGameSession(gameId)) {
            defaultWebSocketServerSession.closeSession(
                message = "You cannot join.",
                reason = "Invalid game session ID."
            )
            return
        }

        val gameSession = GameSession(gameId, playerName, defaultWebSocketServerSession)
        val joined = joinGameSession(gameId, gameSession)

        if (joined) {
            defaultWebSocketServerSession.send("You have joined game session $gameId")

            handleGameSessionCommunication(defaultWebSocketServerSession, gameId)
            leaveGameSession(gameId, gameSession)
        } else {
            defaultWebSocketServerSession.closeSession(
                message = "You cannot join at the moment.",
                reason = "Room is full."
            )
        }
    }


}