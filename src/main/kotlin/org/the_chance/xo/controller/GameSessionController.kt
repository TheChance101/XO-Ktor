package org.the_chance.xo.controller

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.the_chance.xo.data.GameSession
import org.the_chance.xo.data.GameState
import org.the_chance.xo.utils.closeSession
import org.the_chance.xo.utils.generateUUID
import java.util.concurrent.ConcurrentHashMap

class GameSessionController {

    private val gameSessions: ConcurrentHashMap<String, MutableList<GameSession>> = ConcurrentHashMap()
    private val state = MutableStateFlow(GameState())
    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var delayGameJob: Job? = null

    companion object {
        private const val MAX_PLAYERS = 2
    }

    init {
        state.onEach(::broadcast).launchIn(gameScope)
    }

    private fun createGameSession(
        playerNameParams: String?,
        defaultWebSocketServerSession: DefaultWebSocketServerSession
    ): GameSession {
        val gameSessionId = generateUUID()
        val playerName = "$playerNameParams"
        gameSessions[gameSessionId] = mutableListOf()
        return GameSession(
            gameId = gameSessionId,
            playerName = playerName,
            playerRole = 'X',
            session = defaultWebSocketServerSession
        )
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

    suspend fun newGame(
        playerNameParams: String?,
        defaultWebSocketServerSession: DefaultWebSocketServerSession
    ): Char {
        val gameSession = createGameSession(playerNameParams, defaultWebSocketServerSession)
        val gameSessionId = gameSession.gameId
        val playerName = gameSession.playerName


        val joined = joinGameSession(gameSessionId, gameSession)

        if (joined) {
            defaultWebSocketServerSession.send("Created game session with ID: $gameSessionId, Player Name: $playerName Role:${gameSession.playerRole}")
            leaveGameSession(
                sessionId = gameSessionId,
                gameSession = gameSession
            ) // this removes session from concurrent hash map
        } else {
            defaultWebSocketServerSession.closeSession(
                message = "Room is full. You cannot join at the moment.",
                reason = "Room is full."
            )
        }
        return gameSession.playerRole
    }


    suspend fun joinGame(
        gameId: String?,
        playerName: String?,
        defaultWebSocketServerSession: DefaultWebSocketServerSession
    ): Char? {
        if (gameId.isNullOrEmpty() || playerName.isNullOrEmpty()) {
            defaultWebSocketServerSession.close(CloseReason(CloseReason.Codes.NORMAL, "Invalid request."))
            return null
        }

//        if (!isValidGameSession(gameId)) {
//            defaultWebSocketServerSession.closeSession(
//                message = "You cannot join.",
//                reason = "Invalid game session ID."
//            )
//            return
//        }

        val gameSession = GameSession(
            gameId = gameId,
            playerName = playerName,
            playerRole = 'O',
            session = defaultWebSocketServerSession
        )
        val joined = joinGameSession(gameId, gameSession)

        if (joined) {
            defaultWebSocketServerSession.send("You have joined game session $gameId , name: $playerName , Role:${gameSession.playerRole}")
            leaveGameSession(gameId, gameSession) // this removes session from concurrent hash map
        } else {
            defaultWebSocketServerSession.closeSession(
                message = "You cannot join at the moment.",
                reason = "Room is full."
            )
        }
        return gameSession.playerRole
    }

    private suspend fun broadcast(state: GameState) {
        gameSessions.values.forEach { gameSession ->
            gameSession.forEach {
                it.session.send(
                    Json.encodeToString(state)
                )
            }
        }
    }

    fun finishTurn(player: Char, x: Int, y: Int) {
        if (state.value.field[y][x] != null || state.value.winningPlayer != null) {
            return
        }
        if (state.value.playerAtTurn != player) {
            return
        }

        val currentPlayer = state.value.playerAtTurn
        state.update {
            val newField = it.field.also { field ->
                field[y][x] = currentPlayer
            }
            val isBoardFull = newField.all { it.all { it != null } }
            if (isBoardFull) {
                startNewRoundDelayed()
            }
            it.copy(
                playerAtTurn = if (currentPlayer == 'X') 'O' else 'X',
                field = newField,
                isBoardFull = isBoardFull,
                winningPlayer = getWinningPlayer()?.also {
                    startNewRoundDelayed()
                }
            )
        }
    }

    private fun getWinningPlayer(): Char? {
        val field = state.value.field
        return if (field[0][0] != null && field[0][0] == field[0][1] && field[0][1] == field[0][2]) {
            field[0][0]
        } else if (field[1][0] != null && field[1][0] == field[1][1] && field[1][1] == field[1][2]) {
            field[1][0]
        } else if (field[2][0] != null && field[2][0] == field[2][1] && field[2][1] == field[2][2]) {
            field[2][0]
        } else if (field[0][0] != null && field[0][0] == field[1][0] && field[1][0] == field[2][0]) {
            field[0][0]
        } else if (field[0][1] != null && field[0][1] == field[1][1] && field[1][1] == field[2][1]) {
            field[0][1]
        } else if (field[0][2] != null && field[0][2] == field[1][2] && field[1][2] == field[2][2]) {
            field[0][2]
        } else if (field[0][0] != null && field[0][0] == field[1][1] && field[1][1] == field[2][2]) {
            field[0][0]
        } else if (field[0][2] != null && field[0][2] == field[1][1] && field[1][1] == field[2][0]) {
            field[0][2]
        } else null
    }

    private fun startNewRoundDelayed() {
        delayGameJob?.cancel()
        delayGameJob = gameScope.launch {
            delay(5000L)
            state.update {
                it.copy(
                    playerAtTurn = 'X',
                    field = GameState.emptyField(),
                    winningPlayer = null,
                    isBoardFull = false,
                )
            }
        }
    }
}