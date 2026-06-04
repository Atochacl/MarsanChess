package com.marsan.chess.data.repository

import android.content.Context
import com.marsan.chess.data.db.*
import com.marsan.chess.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(context: Context) {

    private val db = MarsanChessDatabase.getInstance(context)
    private val playerDao = db.playerDao()
    private val historyDao = db.gameHistoryDao()
    private val arcadeDao = db.arcadeProgressDao()
    private val tutorialDao = db.tutorialProgressDao()

    // ===================== JUGADOR =====================

    suspend fun getOrCreatePlayer(name: String): PlayerEntity {
        return playerDao.getPlayer(name) ?: PlayerEntity(name = name).also {
            playerDao.insertOrUpdate(it)
        }
    }

    fun observePlayer(name: String): Flow<PlayerEntity?> = playerDao.observePlayer(name)

    // ===================== GUARDAR RESULTADO =====================

    suspend fun saveGameResult(session: GameSession) {
        val won  = if (session.result == GameResult.WIN)  1 else 0
        val lost = if (session.result == GameResult.LOSS) 1 else 0
        val draw = if (session.result == GameResult.DRAW) 1 else 0

        // Asegurar que el jugador exista
        getOrCreatePlayer(session.playerName)

        // Actualizar estadísticas acumulativas
        playerDao.updateStats(
            name   = session.playerName,
            points = session.pointsEarned,
            won    = won,
            lost   = lost,
            draw   = draw
        )

        // Guardar en historial
        historyDao.insert(
            GameHistoryEntity(
                playerName       = session.playerName,
                mode             = session.mode.name,
                difficulty       = session.difficulty.level,
                arcadeRivalId    = session.arcadeRivalId,
                result           = session.result.name,
                pointsEarned     = session.pointsEarned,
                playerTimeSeconds= session.playerTimeSeconds,
                cpuTimeSeconds   = session.cpuTimeSeconds
            )
        )

        // Si es arcade y ganó, desbloquear siguiente rival
        if (session.mode == GameMode.ARCADE && session.result == GameResult.WIN) {
            session.arcadeRivalId?.let { rivalId ->
                arcadeDao.markAsBeaten(rivalId)
                if (rivalId < 7) arcadeDao.unlockRival(rivalId + 1)
            }
        }
    }

    // ===================== RANKING =====================

    fun getTopPlayers(): Flow<List<RankingEntry>> = playerDao.getTopPlayers().map { list ->
        list.mapIndexed { index, entity ->
            RankingEntry(
                position    = index + 1,
                playerName  = entity.name,
                totalPoints = entity.totalPoints,
                gamesPlayed = entity.gamesPlayed
            )
        }
    }

    // ===================== HISTORIAL =====================

    fun getHistoryForPlayer(name: String): Flow<List<GameHistoryEntity>> =
        historyDao.getHistoryForPlayer(name)

    // ===================== ARCADE =====================

    fun getArcadeProgress(): Flow<List<ArcadeProgressEntity>> = arcadeDao.getAllProgress()

    suspend fun initArcadeProgress() {
        // Inicializar si es la primera vez (Manchita ya desbloqueada)
        val existing = arcadeDao.getProgress(1)
        if (existing == null) {
            ArcadeRivals.list.forEach { rival ->
                arcadeDao.saveProgress(
                    ArcadeProgressEntity(
                        rivalId    = rival.id,
                        isUnlocked = rival.id == 1, // Solo Manchita al inicio
                        isBeaten   = false
                    )
                )
            }
        }
    }

    suspend fun registerArcadeAttempt(rivalId: Int) {
        arcadeDao.incrementAttempts(rivalId)
    }

    // ===================== TUTORIAL =====================

    fun getTutorialProgress(): Flow<List<TutorialProgressEntity>> = tutorialDao.getAllProgress()

    suspend fun completeLesson(lessonId: Int) {
        tutorialDao.saveProgress(
            TutorialProgressEntity(
                lessonId    = lessonId,
                isCompleted = true,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun completedLessonsCount(): Int = tutorialDao.completedLessonsCount()

    // ===================== PUNTOS POR DIFICULTAD =====================

    fun calculatePoints(mode: GameMode, difficulty: Difficulty, arcadeRivalId: Int?): Int {
        if (mode == GameMode.ARCADE && arcadeRivalId != null) {
            return ArcadeRivals.list.find { it.id == arcadeRivalId }?.points ?: 0
        }
        return difficulty.points
    }

    companion object {
        @Volatile private var INSTANCE: GameRepository? = null

        fun getInstance(context: Context): GameRepository {
            return INSTANCE ?: synchronized(this) {
                GameRepository(context).also { INSTANCE = it }
            }
        }
    }
}
