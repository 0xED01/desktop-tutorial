package com.yuandian.nightprotocol.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** 每一次策略执行、调度、恢复的真实结果记录。 */
@Entity(tableName = "execution_log")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val event: String,
    val success: Boolean,
    val detail: String,
)

@Dao
interface ExecutionLogDao {
    @Insert
    suspend fun insert(log: ExecutionLog)

    @Query("SELECT * FROM execution_log ORDER BY atMillis DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ExecutionLog>>

    @Query("DELETE FROM execution_log WHERE id NOT IN (SELECT id FROM execution_log ORDER BY id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)
}

@Database(entities = [ExecutionLog::class], version = 1, exportSchema = true)
abstract class LogDatabase : RoomDatabase() {
    abstract fun dao(): ExecutionLogDao

    companion object {
        @Volatile private var instance: LogDatabase? = null

        fun get(context: Context): LogDatabase = instance ?: synchronized(this) {
            instance ?: run {
                // 设备加密存储区：直接启动阶段亦可写入日志。使用绝对路径避免 Room 回落到凭据加密区。
                val de = context.applicationContext.createDeviceProtectedStorageContext()
                Room.databaseBuilder(context.applicationContext, LogDatabase::class.java,
                    de.getDatabasePath("night_protocol_log.db").absolutePath)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
        }
    }
}

/** 统一日志入口：同时写入 Room 与 logcat。 */
class EventLogger(private val dao: ExecutionLogDao) {
    suspend fun log(event: String, success: Boolean, detail: String) {
        android.util.Log.println(if (success) android.util.Log.INFO else android.util.Log.WARN,
            "NightProtocol", "[$event] ok=$success $detail")
        runCatching {
            dao.insert(ExecutionLog(atMillis = System.currentTimeMillis(), event = event, success = success, detail = detail))
            dao.prune(KEEP)
        }
    }

    companion object { const val KEEP = 2000 }
}
