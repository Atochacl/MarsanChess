package com.marsan.chess.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ===================== ENTIDADES =====================

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val name: String,
    val totalPoints: Int = 0,
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val gamesLost: Int = 0,
    val gamesDraw: Int = 0,
    val lastPlayed: Long = System.currentTimeMillis()
)

@Entity(tableName = "game_history")
data class GameHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerName: String,
    val mode: String,           // "NORMAL" | "ARCADE"
    val difficulty: Int,        // 1-5
    val arcadeRivalId: Int?,    // null si es partida normal
    val result: String,         // "WIN" | "LOSS" | "DRAW"
    val pointsEarned: Int,
    val playerTimeSeconds: Long,
    val cpuTimeSeconds: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "arcade_progress")
data class ArcadeProgressEntity(
    @PrimaryKey val rivalId: Int,
    val isUnlocked: Boolean = false,
    val isBeaten: Boolean = false,
    val attempts: Int = 0,
    val lastAttempt: Long? = null
)

@Entity(tableName = "tutorial_progress")
data class TutorialProgressEntity(
    @PrimaryKey val lessonId: Int,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

// ===================== DAOs =====================

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY totalPoints DESC LIMIT 5")
    fun getTopPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE name = :name LIMIT 1")
    suspend fun getPlayer(name: String): PlayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(player: PlayerEntity)

    @Query("""
        UPDATE players SET 
        totalPoints = totalPoints + :points,
        gamesPlayed = gamesPlayed + 1,
        gamesWon = gamesWon + :won,
        gamesLost = gamesLost + :lost,
        gamesDraw = gamesDraw + :draw,
        lastPlayed = :timestamp
        WHERE name = :name
    """)
    suspend fun updateStats(
        name: String,
        points: Int,
        won: Int,
        lost: Int,
        draw: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM players WHERE name = :name LIMIT 1")
    fun observePlayer(name: String): Flow<PlayerEntity?>
}

@Dao
interface GameHistoryDao {
    @Insert
    suspend fun insert(game: GameHistoryEntity)

    @Query("SELECT * FROM game_history WHERE playerName = :name ORDER BY timestamp DESC")
    fun getHistoryForPlayer(name: String): Flow<List<GameHistoryEntity>>

    @Query("SELECT COUNT(*) FROM game_history WHERE playerName = :name")
    suspend fun getGamesCount(name: String): Int
}

@Dao
interface ArcadeProgressDao {
    @Query("SELECT * FROM arcade_progress ORDER BY rivalId ASC")
    fun getAllProgress(): Flow<List<ArcadeProgressEntity>>

    @Query("SELECT * FROM arcade_progress WHERE rivalId = :rivalId")
    suspend fun getProgress(rivalId: Int): ArcadeProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ArcadeProgressEntity)

    @Query("UPDATE arcade_progress SET isBeaten = 1 WHERE rivalId = :rivalId")
    suspend fun markAsBeaten(rivalId: Int)

    @Query("UPDATE arcade_progress SET isUnlocked = 1 WHERE rivalId = :rivalId")
    suspend fun unlockRival(rivalId: Int)

    @Query("UPDATE arcade_progress SET attempts = attempts + 1 WHERE rivalId = :rivalId")
    suspend fun incrementAttempts(rivalId: Int)
}

@Dao
interface TutorialProgressDao {
    @Query("SELECT * FROM tutorial_progress ORDER BY lessonId ASC")
    fun getAllProgress(): Flow<List<TutorialProgressEntity>>

    @Query("SELECT * FROM tutorial_progress WHERE lessonId = :lessonId")
    suspend fun getProgress(lessonId: Int): TutorialProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: TutorialProgressEntity)

    @Query("SELECT COUNT(*) FROM tutorial_progress WHERE isCompleted = 1")
    suspend fun completedLessonsCount(): Int
}

// ===================== BASE DE DATOS =====================

@Database(
    entities = [
        PlayerEntity::class,
        GameHistoryEntity::class,
        ArcadeProgressEntity::class,
        TutorialProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MarsanChessDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameHistoryDao(): GameHistoryDao
    abstract fun arcadeProgressDao(): ArcadeProgressDao
    abstract fun tutorialProgressDao(): TutorialProgressDao

    companion object {
        @Volatile private var INSTANCE: MarsanChessDatabase? = null

        fun getInstance(context: Context): MarsanChessDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MarsanChessDatabase::class.java,
                    "marsan_chess_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
