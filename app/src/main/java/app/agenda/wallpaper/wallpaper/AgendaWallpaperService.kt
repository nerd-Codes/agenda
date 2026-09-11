package app.agenda.wallpaper.wallpaper

import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.graphics.BitmapFactory
import java.io.File

import app.agenda.wallpaper.AgendaApplication
import app.agenda.wallpaper.model.AgendaTask
import app.agenda.wallpaper.model.DeviceCanvasProfile
import app.agenda.wallpaper.model.WallpaperComposition
import app.agenda.wallpaper.model.minimalComposition
import app.agenda.wallpaper.render.WallpaperRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AgendaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AgendaWallpaperEngine()

    inner class AgendaWallpaperEngine : Engine() {
        private val renderer = WallpaperRenderer(applicationContext)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var composition: WallpaperComposition = minimalComposition(DeviceCanvasProfile())
        private var tasks: List<AgendaTask> = emptyList()

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                handler.postDelayed(this, 60_000L)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val repository = (application as AgendaApplication).repository
            scope.launch {
                combine(repository.composition, repository.tasks) { composition, allTasks ->
                    val todayStart = java.time.LocalDate.now()
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    val todayEnd = todayStart + 86400000L - 1
                    val filtered = allTasks.filter { task ->
                        (task.startAtMillis ?: 0) <= todayEnd &&
                        (task.endAtMillis ?: Long.MAX_VALUE) >= todayStart
                    }
                    composition to filtered
                }.collect { (nextComposition, nextTasks) ->
                    composition = nextComposition
                    tasks = nextTasks
                    drawFrame()
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                drawFrame()
                handler.removeCallbacks(tick)
                handler.post(tick)
            } else {
                handler.removeCallbacks(tick)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            scope.cancel()
            super.onDestroy()
        }

        private fun drawFrame() {
            if (surfaceHolder.surface?.isValid != true) return
            val canvas = runCatching { surfaceHolder.lockCanvas() }.getOrNull() ?: return
            try {
                val bgFile = File(applicationContext.filesDir, "saved_wallpaper.png")
                val bgBitmap = if (bgFile.exists()) {
                    try { BitmapFactory.decodeFile(bgFile.absolutePath) } catch (e: Exception) { null }
                } else null
                
                renderer.render(
                    canvas = canvas,
                    width = canvas.width.coerceAtLeast(1),
                    height = canvas.height.coerceAtLeast(1),
                    composition = composition,
                    tasks = tasks,
                    backgroundBitmap = bgBitmap
                )
            } finally {
                runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
