package app.agenda.wallpaper

import android.app.Application
import androidx.room.Room
import app.agenda.wallpaper.data.AgendaDatabase
import app.agenda.wallpaper.data.AgendaPreferences
import app.agenda.wallpaper.data.AgendaRepository

class AgendaApplication : Application() {
    lateinit var repository: AgendaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            applicationContext,
            AgendaDatabase::class.java,
            "agenda.db",
        ).fallbackToDestructiveMigration().build()
        repository = AgendaRepository(
            taskDao = database.taskDao(),
            preferences = AgendaPreferences(applicationContext),
        )
    }
}
