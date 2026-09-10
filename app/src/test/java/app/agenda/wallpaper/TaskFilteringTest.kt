package app.agenda.wallpaper

import app.agenda.wallpaper.data.filterBy
import app.agenda.wallpaper.data.sortBy
import app.agenda.wallpaper.model.AgendaTask
import app.agenda.wallpaper.model.TaskFilter
import app.agenda.wallpaper.model.TaskSort
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskFilteringTest {
    @Test
    fun todayAndOverdueKeepsUndatedLocalTasks() {
        val tasks = listOf(
            AgendaTask(title = "Floating"),
            AgendaTask(title = "Tomorrow", dueAtMillis = LocalDate.now().plusDays(1).millis()),
        )

        val result = tasks.filterBy(TaskFilter.TODAY_AND_OVERDUE)

        assertEquals(listOf("Floating"), result.map { it.title })
    }

    @Test
    fun previewTasksKeepTodayAndUndatedIncompleteTasksOnly() {
        val tasks = listOf(
            AgendaTask(title = "Floating"),
            AgendaTask(title = "Today", dueAtMillis = LocalDate.now().millis()),
            AgendaTask(title = "Completed", completed = true),
            AgendaTask(title = "Overdue", dueAtMillis = LocalDate.now().minusDays(1).millis()),
            AgendaTask(title = "Tomorrow", dueAtMillis = LocalDate.now().plusDays(1).millis()),
        )

        val result = tasks.filterBy(TaskFilter.TODAY).filterNot { it.completed }

        assertEquals(listOf("Floating", "Today"), result.map { it.title })
    }

    @Test
    fun prioritySortPlacesHighestPriorityFirst() {
        val tasks = listOf(
            AgendaTask(title = "Low", priority = 1),
            AgendaTask(title = "High", priority = 4),
            AgendaTask(title = "Mid", priority = 2),
        )

        val result = tasks.sortBy(TaskSort.PRIORITY)

        assertEquals(listOf("High", "Mid", "Low"), result.map { it.title })
    }
}

private fun LocalDate.millis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
