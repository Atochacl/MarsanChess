package com.marsan.chess.data.model

// ── JUGADOR ───────────────────────────────────────────────────────────

data class Player(
    val name: String,
    var totalPoints: Int = 0,
    var gamesPlayed: Int = 0,
    var gamesWon: Int    = 0,
    var gamesLost: Int   = 0,
    var gamesDraw: Int   = 0
)

// ── DIFICULTAD ────────────────────────────────────────────────────────

enum class Difficulty(
    val level: Int,
    val label: String,
    val elo: String,
    val points: Int,
    val depth: Int
) {
    LEVEL_1(1, "Principiante", "200 ELO",   1, 1),
    LEVEL_2(2, "Novato",       "600 ELO",   2, 2),
    LEVEL_3(3, "Intermedio",   "1000 ELO",  3, 3),
    LEVEL_4(4, "Avanzado",     "1600 ELO",  5, 4),
    LEVEL_5(5, "Maestro",      "2200+ ELO", 8, 5);

    companion object {
        fun fromLevel(level: Int) = entries.firstOrNull { it.level == level } ?: LEVEL_3
    }
}

// ── MODO DE JUEGO ─────────────────────────────────────────────────────

enum class GameMode { NORMAL, TIMED }

// ── RESULTADO ─────────────────────────────────────────────────────────

enum class GameResult { WIN, LOSS, DRAW, RESIGN, ONGOING }

// ── RAZÓN DE FIN ──────────────────────────────────────────────────────

enum class GameEndReason {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    TIMEOUT,
    RESIGN,
    ONGOING
}

// ── COLOR DE PIEZAS ───────────────────────────────────────────────────

enum class PlayerPieceColor { WHITE, BLACK, RANDOM }

// ── RIVAL (usado internamente por ChessEngine para estilos de juego) ──

data class ArcadeRival(
    val id: Int,
    val name: String,
    val alias: String,
    val emoji: String,
    val difficultyFloat: Float,
    val engineDepth: Int,
    val points: Int,
    val style: RivalStyle,
    var isUnlocked: Boolean = false,
    var isBeaten: Boolean   = false
)

enum class RivalStyle {
    RANDOM, BEGINNER, AGGRESSIVE, DEFENSIVE, STRATEGIC, CHAOTIC, MASTER
}

// ── SESIÓN DE PARTIDA ─────────────────────────────────────────────────

data class GameSession(
    val playerName: String,
    val mode: GameMode,
    val difficulty: Difficulty,
    val arcadeRivalId: Int?        = null,
    var result: GameResult         = GameResult.ONGOING,
    var endReason: GameEndReason   = GameEndReason.ONGOING,
    var playerTimeSeconds: Long    = 0L,
    var cpuTimeSeconds: Long       = 0L,
    var pointsEarned: Int          = 0,
    val timeLimitSeconds: Long     = 0L,
    val timestamp: Long            = System.currentTimeMillis()
)

// ── RANKING ───────────────────────────────────────────────────────────

data class RankingEntry(
    val position: Int,
    val playerName: String,
    val totalPoints: Int,
    val gamesPlayed: Int
)
