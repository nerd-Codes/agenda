package app.agenda.wallpaper.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TaskEntity::class], version = 3, exportSchema = false)
abstract class AgendaDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
