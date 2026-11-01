package com.example.launcher.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val done: Boolean = false, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "notes")
data class NoteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val body: String, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "pinned_apps")
data class PinnedAppEntity(@PrimaryKey val component: String, val position: Int)
@Entity(tableName = "favourites")
data class FavouriteEntity(@PrimaryKey val phone: String, val name: String)

@Dao interface LauncherDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt") fun tasks(): Flow<List<TaskEntity>>
    @Insert suspend fun addTask(task: TaskEntity)
    @Query("UPDATE tasks SET done = :done WHERE id = :id") suspend fun toggleTask(id: Long, done: Boolean)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)
    @Query("SELECT * FROM notes ORDER BY createdAt DESC") fun notes(): Flow<List<NoteEntity>>
    @Insert suspend fun addNote(note: NoteEntity)
    @Query("SELECT * FROM pinned_apps ORDER BY position") fun pins(): Flow<List<PinnedAppEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun pin(pin: PinnedAppEntity)
    @Query("DELETE FROM pinned_apps WHERE component = :component") suspend fun unpin(component: String)
    @Query("DELETE FROM pinned_apps") suspend fun clearPins()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPins(pins: List<PinnedAppEntity>)
    @Transaction suspend fun replacePins(pins: List<PinnedAppEntity>) {
        clearPins()
        insertPins(pins)
    }
    @Query("SELECT * FROM favourites ORDER BY name LIMIT 4") fun favourites(): Flow<List<FavouriteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun favourite(contact: FavouriteEntity)
    @Query("DELETE FROM favourites WHERE phone = :phone") suspend fun removeFavourite(phone: String)
}

@Database(entities = [TaskEntity::class, NoteEntity::class, PinnedAppEntity::class, FavouriteEntity::class], version = 1, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun dao(): LauncherDao
    companion object {
        @Volatile private var instance: LauncherDatabase? = null
        fun getInstance(context: android.content.Context): LauncherDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LauncherDatabase::class.java, "cry.db").build().also { instance = it }
        }
    }
}
