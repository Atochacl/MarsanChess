package com.marsan.chess.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ===================== ENTIDADES EXISTENTES =====================

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val name: String,
    val totalPoints: Int = 0,
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val gamesLost: Int = 0,
    val gamesDraw: Int = 0,
    val gamesResigned: Int = 0,
    val lastPlayed: Long = System.currentTimeMillis()
)

@Entity(tableName = "game_history")
data class GameHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerName: String,
    val mode: String,
    val difficulty: Int,
    val arcadeRivalId: Int?,
    val result: String,
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

// ===================== NUEVA ENTIDAD: GRABACIÓN DE PARTIDAS =====================

/**
 * Guarda una partida completa con todos sus movimientos en notación UCI.
 *
 * movesJson: lista de movimientos serializada como JSON array de strings UCI
 *   e.g. ["e2e4","e7e5","g1f3","b8c6"]
 *
 * boardStateJson: FEN inicial (siempre posición estándar, pero guardado
 *   por extensibilidad para variantes futuras)
 *
 * mode: "NORMAL" | "TIMED" | "TWO_PLAYER"
 * result: "WIN" | "LOSS" | "DRAW" | "RESIGN" | "ONGOING"
 * playerColor: "WHITE" | "BLACK"
 */
@Entity(tableName = "game_records")
data class GameRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTimestamp: Long = System.currentTimeMillis(),
    val endTimestamp: Long = 0L,
    val playerName: String = "",
    val mode: String = "NORMAL",
    val difficulty: Int = 3,
    val result: String = "ONGOING",
    val playerColor: String = "WHITE",
    val movesJson: String = "[]",          // JSON array de strings UCI
    val totalMoves: Int = 0,
    val durationSeconds: Long = 0L,
    val paletteIdx: Int = 0
)

// ===================== DAOs EXISTENTES =====================

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
        totalPoints  = totalPoints  + :points,
        gamesPlayed  = gamesPlayed  + 1,
        gamesWon     = gamesWon     + :won,
        gamesLost    = gamesLost    + :lost,
        gamesDraw    = gamesDraw    + :draw,
        gamesResigned= gamesResigned+ :resigned,
        lastPlayed   = :timestamp
        WHERE name   = :name
    """)
    suspend fun updateStats(
        name: String,
        points: Int,
        won: Int,
        lost: Int,
        draw: Int,
        resigned: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM players WHERE name = :name LIMIT 1")
    fun observePlayer(name: String): Flow<PlayerEntity?>

    @Query("""
        UPDATE players SET
        totalPoints   = 0,
        gamesPlayed   = 0,
        gamesWon      = 0,
        gamesLost     = 0,
        gamesDraw     = 0,
        gamesResigned = 0
        WHERE name    = :name
    """)
    suspend fun resetStats(name: String)
}

@Dao
interface GameHistoryDao {
    @Insert
    suspend fun insert(game: GameHistoryEntity)

    @Query("SELECT * FROM game_history WHERE playerName = :name ORDER BY timestamp DESC")
    fun getHistoryForPlayer(name: String): Flow<List<GameHistoryEntity>>

    @Query("SELECT COUNT(*) FROM game_history WHERE playerName = :name")
    suspend fun getGamesCount(name: String): Int

    @Query("DELETE FROM game_history WHERE playerName = :name")
    suspend fun deleteHistoryForPlayer(name: String)
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

// ===================== NUEVO DAO: GRABACIONES =====================

@Dao
interface GameRecordDao {
    /** Inserta un nuevo registro y devuelve su ID generado */
    @Insert
    suspend fun insert(record: GameRecordEntity): Long

    /** Actualiza un registro existente (para añadir movimientos progresivamente) */
    @Update
    suspend fun update(record: GameRecordEntity)

    /** Obtiene todas las partidas ordenadas por fecha desc */
    @Query("SELECT * FROM game_records ORDER BY startTimestamp DESC")
    fun getAll(): Flow<List<GameRecordEntity>>

    /** Obtiene una partida por ID */
    @Query("SELECT * FROM game_records WHERE id = :id")
    suspend fun getById(id: Int): GameRecordEntity?

    /** Elimina todas las grabaciones */
    @Query("DELETE FROM game_records")
    suspend fun deleteAll()

    /** Elimina una grabación específica por objeto */
    @Delete
    suspend fun delete(record: GameRecordEntity)

    /** Elimina una grabación por ID */
    @Query("DELETE FROM game_records WHERE id = :id")
    suspend fun deleteById(id: Int)

    /** Cuenta total de grabaciones */
    @Query("SELECT COUNT(*) FROM game_records")
    suspend fun count(): Int
}

// ===================== BASE DE DATOS =====================

@Database(
    entities = [
        PlayerEntity::class,
        GameHistoryEntity::class,
        ArcadeProgressEntity::class,
        TutorialProgressEntity::class,
        GameRecordEntity::class        // ← nueva entidad
    ],
    version = 3,                       // ← bump de versión
    exportSchema = false
)
abstract class MarsanChessDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameHistoryDao(): GameHistoryDao
    abstract fun arcadeProgressDao(): ArcadeProgressDao
    abstract fun tutorialProgressDao(): TutorialProgressDao
    abstract fun gameRecordDao(): GameRecordDao   // ← nuevo DAO

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
