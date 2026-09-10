package app.agenda.wallpaper.data

import app.agenda.wallpaper.model.AgendaTask
import app.agenda.wallpaper.model.DeviceCanvasProfile
import app.agenda.wallpaper.model.TaskFilter
import app.agenda.wallpaper.model.TaskSort
import app.agenda.wallpaper.model.WallpaperComposition
import app.agenda.wallpaper.model.minimalComposition
import app.agenda.wallpaper.model.taskListOnlyComposition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AgendaRepository(
    private val taskDao: TaskDao,
    private val preferences: AgendaPreferences,
) {
    val tasks: Flow<List<AgendaTask>> = taskDao.observeTasks().map { entities ->
        entities.map { it.toModel() }
    }

    val userName: Flow<String> = preferences.userName
    val composition: Flow<WallpaperComposition> = preferences.composition
    val canvasProfile: Flow<DeviceCanvasProfile> = preferences.canvasProfile
    val previewTasks: Flow<List<AgendaTask>> = filteredTasks(TaskFilter.TODAY, TaskSort.MANUAL, 12)
        .map { list -> list.filterNot { it.completed } }

    suspend fun ensureSeedData(profile: DeviceCanvasProfile) {
        preferences.saveCanvasProfile(profile)
        if (taskDao.count() == 0) {
            preferences.saveComposition(minimalComposition(profile))
        }
        val current = composition.first()
        val normalized = taskListOnlyComposition(profile, current)
        if (normalized != current) preferences.saveComposition(normalized)
    }

    fun filteredTasks(
        filter: TaskFilter,
        sort: TaskSort,
        maxTasks: Int,
        project: String? = null,
    ): Flow<List<AgendaTask>> = tasks.map { list ->
        list
            .filterBy(filter, project)
            .sortBy(sort)
            .let { if (maxTasks > 0) it.take(maxTasks) else it }
    }

    suspend fun addTask(task: AgendaTask) {
        taskDao.insert(TaskEntity.fromModel(task))
    }

    suspend fun updateTask(task: AgendaTask) {
        taskDao.update(TaskEntity.fromModel(task))
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        taskDao.setCompleted(id, completed)
    }

    suspend fun deleteTask(task: AgendaTask) {
        taskDao.delete(TaskEntity.fromModel(task))
    }

    suspend fun saveUserName(name: String) {
        preferences.saveUserName(name)
    }

    suspend fun saveComposition(composition: WallpaperComposition) {
        preferences.saveComposition(composition)
    }

    suspend fun updateCanvasProfile(profile: DeviceCanvasProfile) {
        preferences.saveCanvasProfile(profile)
        val current = composition.first()
        val normalized = taskListOnlyComposition(profile, current)
        if (normalized != current) preferences.saveComposition(normalized)
    }
}

fun List<AgendaTask>.filterBy(filter: TaskFilter, project: String? = null): List<AgendaTask> {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    fun AgendaTask.dueDate(): LocalDate? = dueAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
    }
    return filter { task ->
        val matchesProject = project == null || task.project == project
        val date = task.dueDate()
        matchesProject && when (filter) {
            TaskFilter.TODAY -> date == today || date == null
            TaskFilter.OVERDUE -> date != null && date.isBefore(today) && !task.completed
            TaskFilter.TODAY_AND_OVERDUE -> date == null || date == today || (date.isBefore(today) && !task.completed)
            TaskFilter.UPCOMING -> date != null && date.isAfter(today)
            TaskFilter.ALL -> true
        }
    }
}

fun List<AgendaTask>.sortBy(sort: TaskSort): List<AgendaTask> = when (sort) {
    TaskSort.MANUAL -> sortedWith(compareBy<AgendaTask> { it.sortOrder }.thenBy { it.createdAtMillis })
    TaskSort.DUE_DATE -> sortedWith(compareBy<AgendaTask> { it.dueAtMillis ?: Long.MAX_VALUE }.thenBy { it.sortOrder })
    TaskSort.PRIORITY -> sortedWith(compareByDescending<AgendaTask> { it.priority }.thenBy { it.sortOrder })
    TaskSort.PROJECT -> sortedWith(compareBy<AgendaTask> { it.project }.thenBy { it.sortOrder })
}
