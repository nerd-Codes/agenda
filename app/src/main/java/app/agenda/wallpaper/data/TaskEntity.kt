package app.agenda.wallpaper.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.agenda.wallpaper.model.AgendaTask

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    val completed: Boolean,
    val dueAtMillis: Long?,
    val startAtMillis: Long?,
    val endAtMillis: Long?,
    val priority: Int,
    val project: String,
    val projectIcon: String,
    val taskColor: String,
    val attachments: String,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toModel(): AgendaTask = AgendaTask(
        id = id,
        title = title,
        notes = notes,
        completed = completed,
        dueAtMillis = dueAtMillis,
        startAtMillis = startAtMillis,
        endAtMillis = endAtMillis,
        priority = priority,
        project = project,
        projectIcon = projectIcon,
        taskColor = taskColor,
        attachments = attachments,
        sortOrder = sortOrder,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    companion object {
        fun fromModel(task: AgendaTask): TaskEntity = TaskEntity(
            id = task.id,
            title = task.title,
            notes = task.notes,
            completed = task.completed,
            dueAtMillis = task.dueAtMillis,
            startAtMillis = task.startAtMillis,
            endAtMillis = task.endAtMillis,
            priority = task.priority,
            project = task.project.trim(),
            projectIcon = task.projectIcon,
            taskColor = task.taskColor,
            attachments = task.attachments,
            sortOrder = task.sortOrder,
            createdAtMillis = task.createdAtMillis,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}
