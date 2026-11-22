package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "match_records")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
    val modeId: String,
    val roboLevelId: String,
    val diceModeId: String,
    val mapId: String,
    val mapName: String,
    val tokensCount: Int,
    val player1Name: String,
    val player2Name: String,
    val winnerIndex: Int,
    val winnerName: String,
    val durationMs: Long,
    val totalMoves: Int,
    val p1Captures: Int,
    val p2Captures: Int,
    val p1Sixes: Int,
    val p2Sixes: Int,
    val p1Rolls: Int,
    val p2Rolls: Int
)

data class PlayerAggregateStats(
    val totalMatches: Int = 0,
    val p1Wins: Int = 0,
    val p1Losses: Int = 0,
    val roboWins: Int = 0,
    val roboMatches: Int = 0,
    val totalCaptures: Int = 0,
    val averageDurationMs: Long = 0L,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val favoriteMap: String = "—"
)

@Dao
interface MatchHistoryDao {
    @Query("SELECT * FROM match_records ORDER BY timestampMs DESC")
    fun getAllMatches(): Flow<List<MatchRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(record: MatchRecord)

    @Query("DELETE FROM match_records WHERE id = :id")
    suspend fun deleteMatch(id: Long)

    @Query("DELETE FROM match_records")
    suspend fun clearAll()
}

@Database(entities = [MatchRecord::class], version = 1, exportSchema = false)
abstract class DuoLudoDatabase : RoomDatabase() {
    abstract fun matchHistoryDao(): MatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: DuoLudoDatabase? = null

        fun getInstance(context: Context): DuoLudoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DuoLudoDatabase::class.java,
                    "duoludo_history.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class MatchHistoryRepository(private val dao: MatchHistoryDao) {
    val allMatches: Flow<List<MatchRecord>> = dao.getAllMatches()

    val aggregateStats: Flow<PlayerAggregateStats> = allMatches.map { list ->
        if (list.isEmpty()) return@map PlayerAggregateStats()

        val p1Wins = list.count { it.winnerIndex == 0 }
        val p1Losses = list.size - p1Wins
        val roboMatches = list.filter { it.modeId == "robo" }
        val roboWins = roboMatches.count { it.winnerIndex == 0 }
        val totalCaptures = list.sumOf { it.p1Captures + it.p2Captures }
        val avgDuration = list.sumOf { it.durationMs } / list.size

        // Compute current and best win streaks for Player 1 (most recent first)
        var currentStreak = 0
        for (rec in list) {
            if (rec.winnerIndex == 0) currentStreak++ else break
        }
        var bestStreak = 0
        var running = 0
        for (rec in list.asReversed()) {
            if (rec.winnerIndex == 0) {
                running++
                if (running > bestStreak) bestStreak = running
            } else {
                running = 0
            }
        }

        val favMap = list.groupingBy { it.mapName }.eachCount().maxByOrNull { it.value }?.key ?: "—"

        PlayerAggregateStats(
            totalMatches = list.size,
            p1Wins = p1Wins,
            p1Losses = p1Losses,
            roboWins = roboWins,
            roboMatches = roboMatches.size,
            totalCaptures = totalCaptures,
            averageDurationMs = avgDuration,
            currentWinStreak = currentStreak,
            bestWinStreak = bestStreak,
            favoriteMap = favMap
        )
    }

    suspend fun recordMatch(record: MatchRecord) = dao.insertMatch(record)
    suspend fun deleteMatch(id: Long) = dao.deleteMatch(id)
    suspend fun clearAll() = dao.clearAll()
}
