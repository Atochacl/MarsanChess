package com.marsan.chess.data.model

// ===================== JUGADOR =====================
data class Player(
    val name: String,
    var totalPoints: Int = 0,
    var gamesPlayed: Int = 0,
    var gamesWon: Int = 0,
    var gamesLost: Int = 0,
    var gamesDraw: Int = 0
)

// ===================== DIFICULTAD CON ELO =====================
enum class Difficulty(val level: Int, val label: String, val elo: String, val points: Int, val depth: Int) {
    LEVEL_1(1, "Principiante", "~600 ELO",  1, 1),
    LEVEL_2(2, "Aprendiz",     "~900 ELO",  2, 2),
    LEVEL_3(3, "Intermedio",   "~1200 ELO", 3, 3),
    LEVEL_4(4, "Avanzado",     "~1500 ELO", 4, 4),
    LEVEL_5(5, "Experto",      "~1800 ELO", 5, 5);

    companion object {
        fun fromLevel(level: Int) = values().first { it.level == level }
    }
}

// ===================== MODO DE JUEGO =====================
enum class GameMode {
    NORMAL, ARCADE, TUTORIAL
}

// ===================== RESULTADO =====================
enum class GameResult {
    WIN, LOSS, DRAW, RESIGN, ONGOING
}

// ===================== COLOR DE PIEZAS =====================
enum class PlayerPieceColor {
    WHITE, BLACK, RANDOM
}

// ===================== RIVALES ARCADE =====================
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
    var isBeaten: Boolean = false
)

enum class RivalStyle {
    RANDOM, BEGINNER, AGGRESSIVE, DEFENSIVE, STRATEGIC, CHAOTIC, MASTER
}

object ArcadeRivals {
    val list = listOf(
        ArcadeRival(1, "Manchita",  "Gata ajedrecista",    "🐱", 1.0f, 1, 1, RivalStyle.RANDOM,     isUnlocked = true),
        ArcadeRival(2, "Alejandra", "Profesora",           "✈️", 1.5f, 1, 2, RivalStyle.BEGINNER),
        ArcadeRival(3, "Laurita",   "Luchadora",           "🗡️", 1.6f, 2, 2, RivalStyle.AGGRESSIVE),
        ArcadeRival(4, "Maca",      "Defensiva",           "🏰", 2.0f, 2, 3, RivalStyle.DEFENSIVE),
        ArcadeRival(5, "Cristian",  "Estratega",           "🧠", 3.5f, 4, 4, RivalStyle.STRATEGIC),
        ArcadeRival(6, "Lop585",    "Rápida e impredecible","👾", 3.8f, 4, 4, RivalStyle.CHAOTIC),
        ArcadeRival(7, "Marsan",    "Gran Maestro",        "🏆", 5.0f, 5, 5, RivalStyle.MASTER)
    )
}

// ===================== TUTORIAL =====================
data class TutorialLesson(
    val id: Int,
    val title: String,
    val steps: List<TutorialStep>,
    var isCompleted: Boolean = false
)

data class TutorialStep(
    val instruction: String,
    val manchitaExpression: ManchitaExpression,
    val boardSetup: String?,
    val expectedMove: String?,
    val isInteractive: Boolean
)

enum class ManchitaExpression {
    NORMAL, HAPPY, CONFUSED, STAR
}

// ===================== PARTIDA =====================
data class GameSession(
    val playerName: String,
    val mode: GameMode,
    val difficulty: Difficulty,
    val arcadeRivalId: Int? = null,
    var result: GameResult = GameResult.ONGOING,
    var playerTimeSeconds: Long = 0L,
    var cpuTimeSeconds: Long = 0L,
    var pointsEarned: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

// ===================== RANKING =====================
data class RankingEntry(
    val position: Int,
    val playerName: String,
    val totalPoints: Int,
    val gamesPlayed: Int
)
