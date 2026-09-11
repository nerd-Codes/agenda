package app.agenda.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.agenda.wallpaper.data.AgendaRepository
import app.agenda.wallpaper.model.*
import app.agenda.wallpaper.render.AgendaPreviewView
import app.agenda.wallpaper.ui.theme.*
import app.agenda.wallpaper.wallpaper.AgendaWallpaperService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

val NType82 = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(app.agenda.wallpaper.R.font.ntype82_regular))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val repository = (application as AgendaApplication).repository
        val profile = detectCanvasProfile()
        lifecycleScope.launch { repository.ensureSeedData(profile) }
        setContent {
            AgendaTheme { AgendaApp(repository, profile) }
        }
    }

    private fun detectCanvasProfile(): DeviceCanvasProfile {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return DeviceCanvasProfile(
            widthPx = metrics.widthPixels.coerceAtLeast(1),
            heightPx = metrics.heightPixels.coerceAtLeast(1),
            density = metrics.density,
            safeTopPx = (metrics.density * 96).toInt(),
            safeBottomPx = (metrics.density * 72).toInt(),
            lastDetectedAt = System.currentTimeMillis(),
        )
    }
}

private data class AgendaUiState(
    val allTasks: List<AgendaTask> = emptyList(),
    val previewTasks: List<AgendaTask> = emptyList(),
    val composition: WallpaperComposition = minimalComposition(DeviceCanvasProfile()),
    val userName: String = "",
)

private class AgendaViewModel(private val repository: AgendaRepository) : ViewModel() {
    val launchTitle: String = dailyTaskTitles.random()
    val state: StateFlow<AgendaUiState> = combine(
        repository.tasks,
        repository.previewTasks,
        repository.composition,
        repository.userName,
    ) { tasks, previewTasks, composition, userName ->
        AgendaUiState(tasks, previewTasks, composition, userName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    fun saveComposition(composition: WallpaperComposition) = viewModelScope.launch { repository.saveComposition(composition) }
    fun saveUserName(name: String) = viewModelScope.launch { repository.saveUserName(name) }
    fun saveTask(task: AgendaTask) = viewModelScope.launch {
        if (task.id == 0L) repository.addTask(task.copy(sortOrder = state.value.allTasks.size)) 
        else repository.updateTask(task)
    }
    fun setCompleted(task: AgendaTask, completed: Boolean) = viewModelScope.launch { repository.setCompleted(task.id, completed) }
    fun deleteTask(task: AgendaTask) = viewModelScope.launch { repository.deleteTask(task) }
}

private enum class AppTab { HOME, TASKS, CREATE, EDITOR, SETTINGS }

@Composable
private fun AgendaApp(repository: AgendaRepository, detectedProfile: DeviceCanvasProfile) {
    val viewModel: AgendaViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AgendaViewModel(repository) as T
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var previousTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var viewingTask by remember { mutableStateOf<AgendaTask?>(null) }
    var editingTask by remember { mutableStateOf<AgendaTask?>(null) }

    // Back button handling: task detail -> previous tab, create -> previous tab, other tabs -> home, home -> exit
    BackHandler(enabled = viewingTask != null || tab != AppTab.HOME) {
        when {
            viewingTask != null -> viewingTask = null
            tab == AppTab.CREATE -> { tab = previousTab; editingTask = null }
            tab != AppTab.HOME -> tab = AppTab.HOME
        }
    }

    LaunchedEffect(detectedProfile) { repository.updateCanvasProfile(detectedProfile) }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(BrandBlack).padding(top = topPadding)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = viewingTask,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 })
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "taskDetail"
            ) { task ->
                if (task != null) {
                    TaskDetailScreen(
                        task = task,
                        onBack = { viewingTask = null },
                        onEdit = {
                            editingTask = viewingTask
                            viewingTask = null
                            tab = AppTab.CREATE
                        },
                        onDelete = {
                            viewModel.deleteTask(viewingTask!!)
                            viewingTask = null
                        }
                    )
                } else {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { direction * it / 8 })
                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -direction * it / 8 })
                        },
                        label = "tabSwitch"
                    ) { currentTab ->
                        when (currentTab) {
                            AppTab.HOME -> TasksScreen(state, viewModel) { viewingTask = it }
                            AppTab.TASKS -> AllTasksScreen(state, viewModel) { viewingTask = it }
                            AppTab.CREATE -> CreateTaskScreen(
                                initialTask = editingTask,
                                onBack = { tab = previousTab; editingTask = null },
                                onSave = { viewModel.saveTask(it); tab = previousTab; editingTask = null }
                            )
                            AppTab.EDITOR -> PreviewScreen(state, viewModel::saveComposition)
                            AppTab.SETTINGS -> SettingsScreen(state.userName, viewModel::saveUserName)
                        }
                    }
                }
            }
        }

        // Animated navbar visibility
        AnimatedVisibility(
            visible = tab != AppTab.CREATE && viewingTask == null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AppNavigation(tab, { previousTab = tab; tab = it }, { previousTab = tab; editingTask = null; tab = AppTab.CREATE })
        }
    }
}

@Composable
private fun AppNavigation(selected: AppTab, onSelect: (AppTab) -> Unit, onFabClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BrandBlack.copy(alpha=0.2f), BrandBlack, BrandBlack))))
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val path = Path().apply {
                val cornerRadius = 32.dp.toPx()
                val fabRadius = 42.dp.toPx()
                val topPadding = 16.dp.toPx()
                val cx = size.width / 2f
                
                moveTo(0f, topPadding + cornerRadius)
                quadraticTo(0f, topPadding, cornerRadius, topPadding)
                lineTo(cx - fabRadius - 24.dp.toPx(), topPadding)
                cubicTo(
                    cx - fabRadius + 4.dp.toPx(), topPadding,
                    cx - fabRadius - 4.dp.toPx(), topPadding + fabRadius + 12.dp.toPx(),
                    cx, topPadding + fabRadius + 12.dp.toPx()
                )
                cubicTo(
                    cx + fabRadius + 4.dp.toPx(), topPadding + fabRadius + 12.dp.toPx(),
                    cx + fabRadius - 4.dp.toPx(), topPadding,
                    cx + fabRadius + 24.dp.toPx(), topPadding
                )
                
                lineTo(size.width - cornerRadius, topPadding)
                quadraticTo(size.width, topPadding, size.width, topPadding + cornerRadius)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, NavBackground)
        }
        
        Row(Modifier.fillMaxWidth().height(100.dp).padding(bottom = 20.dp, top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val homeTint by animateColorAsState(if (selected == AppTab.HOME) NavActive else NavInactive, tween(250), label = "home")
            val tasksTint by animateColorAsState(if (selected == AppTab.TASKS) NavActive else NavInactive, tween(250), label = "tasks")
            val editorTint by animateColorAsState(if (selected == AppTab.EDITOR) NavActive else NavInactive, tween(250), label = "editor")
            val settingsTint by animateColorAsState(if (selected == AppTab.SETTINGS) NavActive else NavInactive, tween(250), label = "settings")
            IconButton(onClick = { onSelect(AppTab.HOME) }) {
                Icon(Icons.Filled.Home, "Home", tint = homeTint, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { onSelect(AppTab.TASKS) }) {
                Icon(Icons.AutoMirrored.Outlined.Assignment, "Tasks", tint = tasksTint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(64.dp))
            IconButton(onClick = { onSelect(AppTab.EDITOR) }) {
                Icon(Icons.Outlined.ChatBubbleOutline, "Editor", tint = editorTint, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { onSelect(AppTab.SETTINGS) }) {
                Icon(Icons.Outlined.Person, "Settings", tint = settingsTint, modifier = Modifier.size(28.dp))
            }
        }
        
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val fabScale by animateFloatAsState(if (isPressed) 0.88f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "fab")
        Surface(
            shape = CircleShape,
            color = NavActive,
            modifier = Modifier.offset(y = (-40).dp).size(64.dp)
                .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                .clickable(interactionSource = interactionSource, indication = null) { onFabClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, "Add Task", tint = BrandBlack, modifier = Modifier.size(32.dp))
            }
        }
    }
}

private val dailyTaskTitles = listOf(
    "Do the thing.", "Again?", "You got this. Probably.", "Uh oh. Tasks.", "Make it happen.", 
    "The list awaits.", "Begin nonsense.", "Chaos, organized.", "We have tasks.", "Surely, this time.", 
    "Good luck.", "Time to pretend.", "Future you says thanks.", "Oops. Productive.", "Behold: responsibilities.", 
    "Task mode: ON.", "Let's cause progress.", "One thing at a time.", "Tiny steps. Huge drama.", 
    "Procrastination ends here.", "You vs. the list.", "The clock is judging.", "Become unstoppable.", 
    "Unfortunately, we must work.", "Another day, another checklist.", "Things need doing.", "It's on the list.", 
    "Sadly, yes.", "Back to work.", "Carry on.", "Nothing to see here.", "As you were.", "Very productive.", 
    "Probably important.", "This seems necessary.", "Please proceed.", "Work, apparently.", "Life continues.", 
    "Do as instructed.", "You know what to do.", "Congratulations, responsibilities.", "Existing is exhausting.", 
    "Could be worse.", "Let's get this over with.", "Make today count.", "Little wins matter.", "One tiny win.", 
    "Go, go, go!", "You've got this.", "Make some progress.", "Today looks promising.", "Let's make magic.", 
    "Keep going.", "Nice and easy.", "One step closer.", "Tiny task, big win.", "Progress looks good on you.", 
    "Your future self approves.", "Do something wonderful.", "Hydrate first.", "Where's your coffee?", 
    "The ducks approve.", "Be suspiciously productive.", "Touch grass later.", "Feed the brain.", 
    "Activate brain cell.", "Probably need snacks.", "Blink twice. Begin.", "Check the vibes.", 
    "No thoughts. Just tasks.", "System operational.", "Brain.exe loading...", "We ride at dawn.", 
    "Acquire momentum.", "Return to monke.", "Respectfully, focus.", "The mission continues.", 
    "Excellent. A list.", "Everything is fine.", "This is your sign.", "Pretend it's urgent.", 
    "Make future-you proud.", "Don't forget the thing.", "System ready.", "Agenda online.", "Awaiting input.", 
    "Mission initialized.", "Execute today.", "Protocol: progress.", "Daily cycle started.", 
    "Objective detected.", "Status: operational.", "Task sequence ready.", "Human mode: active.", 
    "Productivity protocol engaged.", "Agenda awaits.", "Begin daily protocol.", "Systems nominal.", 
    "Mission control online.", "Another cycle begins.", "Input required.", "Proceed accordingly.", 
    "Make progress.", "What are we doing?", "Why are we here?", "Make it matter.", "Time keeps moving.", 
    "One day at a time.", "Future you is watching.", "Mortality awaits. Start now.", "The days are numbered.", 
    "Might as well begin.", "Time is doing its thing.", "Make today useful.", "This moment counts.", 
    "You only get today.", "Eventually becomes never.", "Begin before you're ready."
)

private val DefaultColors = listOf(
    CardMobile, CardWireframe, CardWebsite, Color(0xFFF3E5F5), Color(0xFFE3F2FD), Color(0xFFE8F5E9)
)

private fun getProjectIcon(name: String): ImageVector {
    return when(name) {
        "Mobile" -> Icons.Outlined.PhoneAndroid
        "Wireframe" -> Icons.Outlined.Lightbulb
        "Website" -> Icons.Outlined.Language
        "Design" -> Icons.Outlined.ColorLens
        else -> Icons.Outlined.Folder
    }
}

private fun parseColor(colorString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        CardMobile
    }
}

private fun colorToHex(color: Color): String {
    return String.format("#%06X", (0xFFFFFF and color.toArgb()))
}

@Composable
private fun TasksScreen(state: AgendaUiState, viewModel: AgendaViewModel, onTaskClick: (AgendaTask) -> Unit) {
    val allTasks = state.allTasks
    val projects = allTasks.filter { it.project.isNotBlank() }.groupBy { it.project }.toList()
    var gridExpanded by remember { mutableStateOf(false) }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = TextGrey)) { append("Hello ") }
                            withStyle(SpanStyle(color = TextWhite)) { append(state.userName.ifBlank { "User" }) }
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(viewModel.launchTitle, color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.displayLarge)
                }
                Surface(shape = CircleShape, border = BorderStroke(1.dp, TextGrey.copy(alpha = 0.3f)), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Notifications, "Notifications", tint = TextWhite)
                        Canvas(Modifier.size(8.dp).offset(x = 8.dp, y = (-8).dp)) {
                            drawCircle(Color(0xFFFF5252))
                        }
                    }
                }
            }
        }

        if (projects.isNotEmpty()) {
            item {
                val displayProjects = if (gridExpanded) projects else projects.take(3)
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (displayProjects.size == 1) {
                        ProjectCard(displayProjects[0], Modifier.fillMaxWidth().height(200.dp))
                    } else if (displayProjects.size == 2) {
                        Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProjectCard(displayProjects[0], Modifier.weight(1f).fillMaxHeight())
                            ProjectCard(displayProjects[1], Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        val chunks = displayProjects.chunked(3)
                        chunks.forEach { chunk ->
                            if (chunk.size == 3) {
                                Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ProjectCard(chunk[0], Modifier.weight(1f).fillMaxHeight())
                                    Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        ProjectCard(chunk[1], Modifier.weight(1f).fillMaxWidth(), horizontal = true)
                                        ProjectCard(chunk[2], Modifier.weight(1f).fillMaxWidth(), horizontal = true)
                                    }
                                }
                            } else if (chunk.size == 2) {
                                Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ProjectCard(chunk[0], Modifier.weight(1f).fillMaxHeight())
                                    ProjectCard(chunk[1], Modifier.weight(1f).fillMaxHeight())
                                }
                            } else if (chunk.size == 1) {
                                ProjectCard(chunk[0], Modifier.fillMaxWidth().height(100.dp), horizontal = true)
                            }
                        }
                    }
                    if (projects.size > 3) {
                        TextButton(onClick = { gridExpanded = !gridExpanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (gridExpanded) "Show Less" else "Expand All Projects", color = TextGrey)
                        }
                    }
                }
            }
            
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ongoing", color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                    Text("See All", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            item { Spacer(Modifier.height(24.dp)) }
        }

        items(allTasks.sortedBy { it.completed }, key = { it.id }) { task ->
            TaskRow(task, modifier = Modifier.animateItem(), onClick = { onTaskClick(task) }, onCheck = { viewModel.setCompleted(task, it) })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProjectCard(projectData: Pair<String, List<AgendaTask>>, modifier: Modifier, horizontal: Boolean = false) {
    val (projectName, tasks) = projectData
    val task = tasks.firstOrNull()
    val bgColor = task?.taskColor?.let { parseColor(it) } ?: CardMobile
    val iconName = task?.projectIcon ?: "Folder"
    val icon = getProjectIcon(iconName)
    
    Surface(shape = RoundedCornerShape(24.dp), color = bgColor, modifier = modifier) {
        if (horizontal) {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, projectName, tint = BrandBlack, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(projectName, color = BrandBlack, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text("${tasks.size} Tasks", color = BrandBlack.copy(alpha = 0.7f), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, projectName, tint = BrandBlack, modifier = Modifier.size(36.dp))
                Column {
                    Text(projectName, color = BrandBlack, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text("${tasks.size} Tasks", color = BrandBlack.copy(alpha = 0.7f), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun TaskRow(task: AgendaTask, modifier: Modifier = Modifier, onClick: () -> Unit, onCheck: (Boolean) -> Unit) {
    val progress = (task.priority * 25).coerceIn(0, 100)
    val priorityLabel = when(task.priority) {
        1 -> "Low"
        2 -> "Medium"
        3 -> "High"
        else -> "Urgent"
    }
    val pillColor = if (task.priority >= 3) PillHigh else if (task.priority == 2) PillMedium else Color(0xFF4CAF50)
    
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardDark,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).alpha(if (task.completed) 0.5f else 1f).clickable { onClick() }
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = pillColor) {
                    Text(priorityLabel, color = if (task.priority >= 3) TextWhite else BrandBlack, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                
            }
            Spacer(Modifier.height(16.dp))
            Text(task.title, color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, "Time", tint = TextGrey, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                val timeStr = if (task.startAtMillis != null && task.endAtMillis != null) {
                    val s = Instant.ofEpochMilli(task.startAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                    val e = Instant.ofEpochMilli(task.endAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                    val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
                    "${s.format(fmt)} - ${e.format(fmt)}"
                } else "10:00 AM - 06:00 PM"
                Text(timeStr, color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Due Date: ", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                val dueStr = task.dueAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMMM d", Locale.US))
                } ?: "August 25"
                Text(dueStr, color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.IconButton(
                    onClick = { onCheck(!task.completed) },
                    modifier = Modifier.size(28.dp)
                ) {
                    if (task.completed) {
                        Icon(Icons.Outlined.CheckCircle, "Completed", tint = NavActive)
                    } else {
                        Icon(Icons.Outlined.RadioButtonUnchecked, "Mark as done", tint = TextGrey)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskScreen(initialTask: AgendaTask?, onBack: () -> Unit, onSave: (AgendaTask) -> Unit) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var project by remember { mutableStateOf(initialTask?.project ?: "") }
    var projectIcon by remember { mutableStateOf(initialTask?.projectIcon ?: "ColorLens") }
    var notes by remember { mutableStateOf(initialTask?.notes ?: "") }
    var taskColor by remember { mutableStateOf(initialTask?.taskColor ?: colorToHex(CardMobile)) }
    var priority by remember { mutableStateOf(initialTask?.priority ?: 3) }
    var startAtMillis by remember { mutableStateOf(initialTask?.startAtMillis ?: todayStartMillis()) }
    var endAtMillis by remember { mutableStateOf(initialTask?.endAtMillis ?: todayStartMillis()) }
    var dueAtMillis by remember { mutableStateOf(initialTask?.dueAtMillis ?: todayStartMillis()) }
    var attachments by remember { mutableStateOf(initialTask?.attachments?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }
    val context = LocalContext.current
    val attachmentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val newAttachments = uris.mapNotNull { uri ->
            try {
                var fileName = "attachment_"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    }
                }
                val localFile = java.io.File(context.filesDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                localFile.absolutePath
            } catch (e: Exception) { null }
        }
        attachments = attachments + newAttachments
    }
    
    var showStartDatePicker by remember { mutableStateOf(false) }
    
    
    val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = startAtMillis)
    
    
        var showDueDatePicker by remember { mutableStateOf(false) }
    val dueDatePickerState = rememberDatePickerState(initialSelectedDateMillis = dueAtMillis)

    if (showDueDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDatePickerState.selectedDateMillis?.let { dateMillis -> 
                        val cal = java.util.Calendar.getInstance()
                        cal.timeInMillis = dueAtMillis
                        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val currentMin = cal.get(java.util.Calendar.MINUTE)
                        
                        android.app.TimePickerDialog(context, { _, hour, minute ->
                            val selectedCal = java.util.Calendar.getInstance()
                            selectedCal.timeInMillis = dateMillis
                            selectedCal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                            selectedCal.set(java.util.Calendar.MINUTE, minute)
                            dueAtMillis = selectedCal.timeInMillis
                        }, currentHour, currentMin, false).show()
                    }
                    showDueDatePicker = false
                }) { Text("OK", color = NavActive) }
            }
        ) { DatePicker(state = dueDatePickerState) }
    }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { startAtMillis = it }
                    showStartDatePicker = false
                }) { Text("OK", color = NavActive) }
            }
        ) { DatePicker(state = startDatePickerState) }
    }
    

    
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize().background(BrandBlack)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, "Close", tint = TextWhite) }
                Spacer(Modifier.width(16.dp))
                Text(if (initialTask == null) "Create New Task" else "Edit Task", color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
            }
            
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text("Task Name", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    BasicTextField(
                        value = title, onValueChange = { title = it },
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = TextWhite),
                        cursorBrush = SolidColor(NavActive),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        decorationBox = { innerTextField ->
                            Column {
                                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    if (title.isEmpty()) Text("Task title...", color = TextGrey.copy(alpha=0.5f))
                                    innerTextField()
                                }
                                HorizontalDivider(color = TextGrey.copy(alpha=0.3f), thickness = 1.dp)
                            }
                        }
                    )
                }

                Column {
                    Text("Project Name", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    BasicTextField(
                        value = project, onValueChange = { project = it },
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = TextWhite),
                        cursorBrush = SolidColor(NavActive),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        decorationBox = { innerTextField ->
                            Column {
                                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    if (project.isEmpty()) Text("Categorize task...", color = TextGrey.copy(alpha=0.5f))
                                    innerTextField()
                                }
                                HorizontalDivider(color = TextGrey.copy(alpha=0.3f), thickness = 1.dp)
                            }
                        }
                    )
                }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Start Date", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { showStartDatePicker = true }) {
                            Column {
                                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val startFmt = Instant.ofEpochMilli(startAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
                                    Text(startFmt, color = TextWhite)
                                    Icon(Icons.Outlined.CalendarToday, null, tint = TextGrey, modifier = Modifier.size(18.dp))
                                }
                                HorizontalDivider(color = TextGrey.copy(alpha=0.3f), thickness = 1.dp)
                            }
                        }
                    }

                }

                Column {
                    Text("Deadline", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { showDueDatePicker = true }) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                val dueFmt = Instant.ofEpochMilli(dueAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US))
                                Text(dueFmt, color = TextWhite)
                                Icon(Icons.Outlined.Schedule, null, tint = TextGrey, modifier = Modifier.size(18.dp))
                            }
                            HorizontalDivider(color = TextGrey.copy(alpha=0.3f), thickness = 1.dp)
                        }
                    }
                }

                Column {
                    Text("Priority", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("Low" to 1, "Medium" to 2, "High" to 3).forEach { (label, pValue) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (priority == pValue) NavActive else CardDark,
                                modifier = Modifier.clickable { priority = pValue }
                            ) {
                                Text(label, color = if (priority == pValue) BrandBlack else TextWhite, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = TextGrey.copy(alpha=0.3f), thickness = 1.dp)
                }
                
                Column {
                    Text("Description", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    BasicTextField(
                        value = notes, onValueChange = { notes = it },
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = TextWhite),
                        cursorBrush = SolidColor(NavActive),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        decorationBox = { innerTextField ->
                            Column {
                                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    if (notes.isEmpty()) Text("Hey guys! ??...", color = TextGrey.copy(alpha=0.5f))
                                    innerTextField()
                                }
                                HorizontalDivider(color = TextGrey.copy(alpha=0.3f), thickness = 1.dp)
                            }
                        }
                    )
                }

                Column {
                    Text("Attachments", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, TextGrey), color = Color.Transparent, modifier = Modifier.size(64.dp).clickable { attachmentLauncher.launch(arrayOf("*/*")) }) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Add", tint = TextGrey) }
                            }
                        }
                        items(attachments) { path ->
                            AttachmentPreview(path)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = TextGrey.copy(alpha=0.3f), thickness = 1.dp)
                }
                
                Column {
                    Text("Pick Task Color", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(DefaultColors) { color ->
                            val hex = colorToHex(color)
                            Surface(
                                shape = CircleShape,
                                color = color,
                                border = if (taskColor == hex) BorderStroke(2.dp, TextWhite) else null,
                                modifier = Modifier.size(36.dp).clickable { taskColor = hex }
                            ) {}
                        }
                    }
                }
                
                Spacer(Modifier.height(160.dp))
            }
        }
        
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Transparent, BrandBlack, BrandBlack))), contentAlignment = Alignment.BottomCenter) {
            Button(
                onClick = {
                    val task = (initialTask ?: AgendaTask(title = title.ifBlank { "Untitled" })).copy(
                        title = title.ifBlank { "Untitled" },
                        project = project.trim(),
                        projectIcon = projectIcon,
                        notes = notes,
                        taskColor = taskColor,
                                attachments = attachments.joinToString(","),
                        priority = priority,
                        startAtMillis = startAtMillis,
                        endAtMillis = java.util.Calendar.getInstance().apply { timeInMillis = dueAtMillis; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis,
                        dueAtMillis = dueAtMillis
                    )
                    onSave(task)
                },
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavActive, contentColor = BrandBlack),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (initialTask == null) "Create Task" else "Save Changes", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
        }
    }
}
@Composable
private fun TaskDetailScreen(task: AgendaTask, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val bgColor = parseColor(task.taskColor)
    val priorityLabel = when(task.priority) {
        1 -> "Low"
        2 -> "Medium"
        3 -> "High"
        else -> "Urgent"
    }
    val pillColor = if (task.priority >= 3) PillHigh else if (task.priority == 2) PillMedium else Color(0xFF4CAF50)

    Column(Modifier.fillMaxSize().background(bgColor)) {
        Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = BrandBlack) }
            Row {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = BrandBlack) }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit", tint = BrandBlack) }
            }
        }
        
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            Text(task.title, color = BrandBlack, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            
            val dateFmt = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
            val stDate = task.startAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt) } ?: "August 12, 2023"
            Row { Text("Start Date", color = BrandBlack.copy(alpha=0.6f), modifier = Modifier.width(100.dp)); Text(stDate, color = BrandBlack) }
            Spacer(Modifier.height(16.dp))
            Row { Text("Project", color = BrandBlack.copy(alpha=0.6f), modifier = Modifier.width(100.dp)); Text(if (task.project.isNotBlank()) task.project else "Unassigned", color = BrandBlack) }
            Spacer(Modifier.height(16.dp))
            val dueFmt = task.dueAtMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", java.util.Locale.US)) } ?: "Not set"
            Row { Text("Deadline", color = BrandBlack.copy(alpha=0.6f), modifier = Modifier.width(100.dp)); Text(dueFmt, color = BrandBlack) }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Text("Priority", color = BrandBlack.copy(alpha=0.6f), modifier = Modifier.width(100.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = pillColor) {
                    Text(priorityLabel, color = if (task.priority >= 3) TextWhite else BrandBlack, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        
        Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), color = BrandBlack) {
            Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text("Description", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(task.notes.ifBlank { "No description provided." }, color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(32.dp))
                
                val attachmentsList = task.attachments.split(",").filter { it.isNotBlank() }
                if (attachmentsList.isNotEmpty()) {
                    Text("Attachments", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(attachmentsList) { path ->
                            AttachmentPreview(path)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                } else {
                    Text("Attachments", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("No attachments", color = TextGrey.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(32.dp))
                }
                
                Text("Task Color", color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Selected Color", color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Surface(shape = CircleShape, color = bgColor, modifier = Modifier.size(24.dp)) {}
                }
            }
        }
    }
}

@Composable
private fun PreviewScreen(state: AgendaUiState, onSave: (WallpaperComposition) -> Unit) {
    val context = LocalContext.current
    // Store the Android Bitmap directly so we can pass it to the renderer
    var bgAndroidBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val pickBgLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bmp != null) {
                    bgAndroidBitmap = bmp
                    // Save to internal storage for the live wallpaper to read
                    kotlin.concurrent.thread {
                        try {
                            val file = java.io.File(context.filesDir, "saved_wallpaper.png")
                            java.io.FileOutputStream(file).use { out ->
                                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Load saved background on startup
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = java.io.File(context.filesDir, "saved_wallpaper.png")
            if (file.exists()) {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) bgAndroidBitmap = bmp
                } catch (_: Exception) {}
            }
        }
    }

    // Use real screen pixel dimensions (same as what the wallpaper surface gets)
    val displayMetrics = context.resources.displayMetrics
    val realWidthPx = displayMetrics.widthPixels.toFloat()
    val realHeightPx = displayMetrics.heightPixels.toFloat()

    val savedElem = state.composition.element("main_tasks")
    var offsetX by remember(savedElem) { mutableStateOf((savedElem?.bounds?.x ?: 0.1f) * realWidthPx) }
    var offsetY by remember(savedElem) { mutableStateOf((savedElem?.bounds?.y ?: 0.2f) * realHeightPx) }
    var scale by remember(savedElem) { mutableStateOf(savedElem?.scale ?: 1f) }

    val todayStart = todayStartMillis()
    val todayEnd = todayStart + 86400000L - 1
    val todayTasks = state.allTasks.filter { (it.startAtMillis ?: 0) <= todayEnd && (it.endAtMillis ?: Long.MAX_VALUE) >= todayStart }

    val renderer = remember { app.agenda.wallpaper.render.WallpaperRenderer(context) }

    Box(Modifier.fillMaxSize()) {
        // Single Canvas that renders EVERYTHING (background + tasks) via WallpaperRenderer
        // This is the exact same renderer the live wallpaper uses.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                val normalizedX = offsetX / realWidthPx
                val normalizedY = offsetY / realHeightPx
                val elem = WallpaperElement(
                    id = "main_tasks",
                    type = ElementType.TASK_LIST,
                    bounds = NormalizedRect(normalizedX, normalizedY, 0.8f, 0.5f),
                    scale = scale
                )
                val comp = state.composition.copy(elements = listOf(elem))
                // Pass the SAME background bitmap the live wallpaper uses
                renderer.render(
                    canvas = canvas,
                    width = realWidthPx.toInt(),
                    height = realHeightPx.toInt(),
                    composition = comp,
                    tasks = todayTasks,
                    backgroundBitmap = bgAndroidBitmap
                )
            }
        }

        Row(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 24.dp)) {
            Button(
                onClick = { pickBgLauncher.launch("image/*") },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = TextWhite)
            ) {
                Text("Pick Background")
            }
        }

        Button(
            onClick = {
                val normalizedX = offsetX / realWidthPx
                val normalizedY = offsetY / realHeightPx
                val elem = WallpaperElement(
                    id = "main_tasks",
                    type = ElementType.TASK_LIST,
                    bounds = NormalizedRect(normalizedX, normalizedY, 0.8f, 0.5f),
                    scale = scale
                )
                val newComp = state.composition.copy(elements = listOf(elem))
                onSave(newComp)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 140.dp, end = 24.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NavActive, contentColor = BrandBlack)
        ) {
            Text("Save Layout")
        }
    }
}

@Composable
fun WidgetOverlayUI(tasks: List<AgendaTask>) {
    val day = java.time.LocalDate.now()
    Row(Modifier.width(360.dp).height(IntrinsicSize.Min).padding(horizontal = 24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).fillMaxHeight()) {
            Spacer(Modifier.height(24.dp))
            Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US), color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, TextGrey), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.dayOfMonth.toString(), color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(1.dp).weight(1f).background(CardDark))
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(Modifier.weight(1f).padding(top = 32.dp, bottom = 16.dp)) {
            if (tasks.isEmpty()) {
                Text("No tasks for today.", color = TextGrey, modifier = Modifier.padding(start = 16.dp))
            } else {
                tasks.take(5).forEach { task ->
                    val bgColor = try { parseColor(task.taskColor) } catch (e: Exception) { Color.LightGray }
                    Surface(shape = RoundedCornerShape(8.dp), color = bgColor, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            val projName = if (task.project.isNotBlank()) task.project else "Unassigned"
                            Text(projName, color = BrandBlack.copy(alpha=0.6f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(task.title, color = BrandBlack, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontFamily = NType82)
                            if (task.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(task.notes, color = BrandBlack.copy(alpha=0.8f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(userName: String, onNameChange: (String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(userName) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Settings", color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it; onNameChange(it) },
                label = { Text("Your Name", color = TextGrey) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavActive,
                    unfocusedBorderColor = TextGrey,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = NavActive
                )
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, android.content.ComponentName(context, app.agenda.wallpaper.wallpaper.AgendaWallpaperService::class.java))
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NavActive, contentColor = BrandBlack)
            ) {
                Text("Set Live Wallpaper")
            }
        }
    }
}

private fun todayStartMillis(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllTasksScreen(state: AgendaUiState, viewModel: AgendaViewModel, onTaskClick: (AgendaTask) -> Unit) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val days = remember { (-15..30).map { LocalDate.now().plusDays(it.toLong()) } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = 15 - 3)
    var showMonthPicker by remember { mutableStateOf(false) }

    if (showMonthPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showMonthPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showMonthPicker = false
                }) { Text("OK", color = NavActive) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showMonthPicker = true }) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.ExpandMore, null, tint = TextGrey)
            }
        }

        LazyRow(state = listState, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items(days) { day ->
                val isSelected = day == selectedDate
                val isToday = day == LocalDate.now()
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedDate = day }) {
                    Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US), color = TextGrey, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = CircleShape, 
                        color = if(isSelected) NavActive else Color.Transparent, 
                        border = if(isToday && !isSelected) BorderStroke(1.dp, NavActive) else null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(day.dayOfMonth.toString(), color = if(isSelected) BrandBlack else TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = CardDark, thickness = 1.dp)

        val listDates = remember(selectedDate, state.allTasks) {
            (0..90).map { selectedDate.plusDays(it.toLong()) }.filter { d -> 
                d == selectedDate || state.allTasks.any { t -> 
                    val s = t.startAtMillis ?: 0
                    val e = t.endAtMillis ?: Long.MAX_VALUE
                    val ds = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val de = ds + 86400000L - 1
                    s <= de && e >= ds 
                }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(listDates) { day ->
                val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayEnd = dayStart + 86400000L - 1
                val filteredTasks = state.allTasks.filter { (it.startAtMillis ?: 0) <= dayEnd && (it.endAtMillis ?: Long.MAX_VALUE) >= dayStart }

                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).fillMaxHeight()) {
                        Spacer(Modifier.height(24.dp))
                        Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US), color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, TextGrey), modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(day.dayOfMonth.toString(), color = TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(1.dp).weight(1f).background(CardDark))
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(Modifier.weight(1f).padding(top = 32.dp, bottom = 16.dp)) {
                        if (filteredTasks.isEmpty()) {
                            Text("No tasks for this day.", color = TextGrey, modifier = Modifier.padding(start = 16.dp))
                        } else {
                            filteredTasks.forEach { task ->
                                val bgColor = parseColor(task.taskColor)
                                Surface(shape = RoundedCornerShape(8.dp), color = bgColor, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 12.dp).alpha(if (task.completed) 0.5f else 1f).clickable { onTaskClick(task) }) {
                                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                                        val taskDueLocalDate = task.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                                        if (taskDueLocalDate == day && task.dueAtMillis != null) {
                                            val dueTimeStr = Instant.ofEpochMilli(task.dueAtMillis!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                                            Text(dueTimeStr, color = BrandBlack.copy(alpha=0.6f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                                        }
                                        Column(Modifier.padding(16.dp).padding(end = 40.dp)) {
                                            val projName = if (task.project.isNotBlank()) task.project else "Unassigned"
                                        Text(projName, color = BrandBlack.copy(alpha=0.6f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                        Spacer(Modifier.height(8.dp))
                                        Text(task.title, color = BrandBlack, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontFamily = NType82)
                                        if (task.notes.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(task.notes, color = BrandBlack.copy(alpha=0.8f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontFamily = NType82)
                                        }
                                    }
                                    androidx.compose.material3.IconButton(
                                        onClick = { viewModel.setCompleted(task, !task.completed) },
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(28.dp)
                                    ) {
                                        if (task.completed) {
                                            Icon(Icons.Outlined.CheckCircle, "Completed", tint = BrandBlack)
                                        } else {
                                            Icon(Icons.Outlined.RadioButtonUnchecked, "Mark as done", tint = BrandBlack.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(160.dp)) }
        }
    }
}

@Composable
private fun AttachmentPreview(filePath: String) {
    val context = LocalContext.current
    val file = java.io.File(filePath)
    val ext = file.extension.lowercase(Locale.US)
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif")
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardDark,
        modifier = Modifier.size(64.dp).clickable {
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "app.agenda.wallpaper.provider", file)
                var mimeType = context.contentResolver.getType(uri)
                if (mimeType == null) {
                    mimeType = if (isImage) "image/*" else if (ext == "pdf") "application/pdf" else if (ext in listOf("mp4", "mkv")) "video/*" else "*/*"
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) { 
                e.printStackTrace() 
                android.widget.Toast.makeText(context, "Could not open attachment", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isImage) {
                val bitmap = remember(filePath) { android.graphics.BitmapFactory.decodeFile(filePath) }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Attachment",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Outlined.Image, "Image", tint = TextWhite)
                }
            } else {
                val icon = when (ext) {
                    "pdf" -> Icons.Outlined.PictureAsPdf
                    "mp4", "mkv", "avi" -> Icons.Outlined.VideoFile
                    "doc", "docx", "txt" -> Icons.Outlined.Description
                    else -> Icons.Outlined.InsertDriveFile
                }
                Icon(icon, "File", tint = TextWhite)
            }
        }
    }
}
