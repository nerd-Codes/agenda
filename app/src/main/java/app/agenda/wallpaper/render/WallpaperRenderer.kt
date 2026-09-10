package app.agenda.wallpaper.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import app.agenda.wallpaper.R
import app.agenda.wallpaper.model.AgendaTask
import app.agenda.wallpaper.model.ElementColor
import app.agenda.wallpaper.model.ElementType
import app.agenda.wallpaper.model.TaskVisualStyle
import app.agenda.wallpaper.model.TextAlignment
import app.agenda.wallpaper.model.WallpaperComposition
import app.agenda.wallpaper.model.WallpaperElement
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class WallpaperRenderer(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val displayTypeface = context.resources.getFont(R.font.ntype82_regular)
    private val bodyTypeface = Typeface.DEFAULT
    private val monoTypeface = Typeface.MONOSPACE
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bodyTypeface }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        composition: WallpaperComposition,
        tasks: List<AgendaTask>,
        selectedElementId: String? = null,
        editorOverlays: Boolean = false,
        backgroundBitmap: android.graphics.Bitmap? = null
    ) {
        if (backgroundBitmap != null) {
            val scale = kotlin.math.max(width.toFloat() / backgroundBitmap.width, height.toFloat() / backgroundBitmap.height)
            val scaledWidth = backgroundBitmap.width * scale
            val scaledHeight = backgroundBitmap.height * scale
            val left = (width - scaledWidth) / 2f
            val top = (height - scaledHeight) / 2f
            val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
            canvas.drawBitmap(backgroundBitmap, null, destRect, null)
        } else {
            canvas.drawColor(composition.background.color.toArgb())
        }
        composition.elements.filter { it.visible }.forEach { element ->
            drawElement(canvas, width, height, element, tasks)
            if (editorOverlays && element.id == selectedElementId) drawSelection(canvas, width, height, element)
        }
    }

    private fun drawElement(
        canvas: Canvas,
        width: Int,
        height: Int,
        element: WallpaperElement,
        tasks: List<AgendaTask>,
    ) {
        val rect = RectF(
            element.bounds.x * width,
            element.bounds.y * height,
            (element.bounds.x + element.bounds.width) * width,
            (element.bounds.y + element.bounds.height) * height,
        )
        when (element.type) {
            ElementType.TASK_LIST -> drawTaskList(canvas, rect, element, tasks)
            ElementType.CLOCK -> drawTextBlock(canvas, rect, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), element, displayTypeface)
            ElementType.DATE -> drawTextBlock(canvas, rect, LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM", Locale.US)).uppercase(Locale.US), element, bodyTypeface)
            ElementType.DAY -> drawTextBlock(canvas, rect, LocalDate.now().dayOfWeek.name, element, bodyTypeface)
            ElementType.TASK_COUNT -> drawTextBlock(canvas, rect, tasks.count { !it.completed }.toString(), element, displayTypeface)
            ElementType.PROGRESS, ElementType.LINE -> drawRule(canvas, rect, element)
            ElementType.PROJECT -> drawTextBlock(canvas, rect, tasks.firstOrNull()?.project ?: "LOCAL", element, monoTypeface)
            ElementType.CUSTOM_TEXT -> drawTextBlock(canvas, rect, element.style.label.ifBlank { "AGENDA" }, element, bodyTypeface)
            ElementType.GRID -> Unit
        }
    }

    private fun drawTaskList(canvas: Canvas, rect: RectF, element: WallpaperElement, tasks: List<AgendaTask>) {
        drawTimeline(canvas, rect, element, tasks)
    }

    private fun drawTimeline(canvas: Canvas, rect: RectF, element: WallpaperElement, tasks: List<AgendaTask>) {
        val displayTasks = tasks.sortedBy { it.completed }.take(5)
        val sdp = density * element.scale
        
        val date = LocalDate.now()
        val dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
        val dayNum = date.dayOfMonth.toString()
        
        val railStartX = rect.left + 24f * sdp
        val railWidth = 40f * sdp
        val railCenterX = railStartX + railWidth / 2f
        
        var currentY = rect.top + 24f * sdp
        
        // Day name - NType82
        val dayNameSize = 12f * sdp
        textPaint.textSize = dayNameSize
        textPaint.typeface = displayTypeface
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        
        val dayNameHeight = textPaint.descent() - textPaint.ascent()
        canvas.drawText(dayName, railCenterX, currentY - textPaint.ascent(), textPaint)
        
        currentY += dayNameHeight + 8f * sdp
        
        // Date circle
        val circleSize = 40f * sdp
        val circleRadius = circleSize / 2f
        linePaint.color = Color.GRAY
        linePaint.strokeWidth = 1f * sdp
        canvas.drawCircle(railCenterX, currentY + circleRadius, circleRadius, linePaint)
        
        // Day number - NType82
        val dayNumSize = 16f * sdp
        textPaint.textSize = dayNumSize
        textPaint.typeface = displayTypeface
        val dayNumBaselineOffset = (textPaint.descent() - textPaint.ascent()) / 2f - textPaint.descent()
        canvas.drawText(dayNum, railCenterX, currentY + circleRadius + dayNumBaselineOffset, textPaint)
        
        currentY += circleSize + 8f * sdp
        
        val lineStartY = currentY
        var taskBottomY = currentY
        
        val taskStartX = rect.left + 80f * sdp
        val taskWidth = 256f * sdp
        val maxTextWidth = taskWidth - 32f * sdp
        
        var taskY = rect.top + 32f * sdp
        textPaint.textAlign = Paint.Align.LEFT
        
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        
        if (displayTasks.isEmpty()) {
            textPaint.textSize = 14f * sdp
            textPaint.typeface = displayTypeface
            textPaint.color = Color.LTGRAY
            canvas.drawText("No tasks for today.", taskStartX + 16f * sdp, taskY - textPaint.ascent(), textPaint)
            taskBottomY = taskY + 30f * sdp
        } else {
            displayTasks.forEach { task ->
                val projName = if (task.project.isNotBlank()) task.project else "Unassigned"
                val notes = task.notes
                val hasNotes = notes.isNotBlank()
                
                // Measure project label
                val projSize = 11f * sdp
                textPaint.textSize = projSize
                textPaint.typeface = bodyTypeface
                val projLineH = textPaint.descent() - textPaint.ascent()
                
                // Measure title
                val titleSize = 18f * sdp
                textPaint.textSize = titleSize
                textPaint.typeface = displayTypeface
                val titleLineH = textPaint.descent() - textPaint.ascent()
                
                // Measure and wrap description lines
                val descSize = 12f * sdp
                var descLines = emptyList<String>()
                var descLineH = 0f
                if (hasNotes) {
                    textPaint.textSize = descSize
                    textPaint.typeface = displayTypeface
                    descLineH = textPaint.descent() - textPaint.ascent()
                    descLines = wrapText(notes.replace('\n', ' '), maxTextWidth, textPaint)
                }
                
                // Calculate bubble height
                var contentH = 16f * sdp + projLineH + 8f * sdp + titleLineH + 16f * sdp
                if (descLines.isNotEmpty()) contentH += 4f * sdp + descLineH * descLines.size
                
                val bubbleRect = RectF(taskStartX, taskY, taskStartX + taskWidth, taskY + contentH)
                bubblePaint.color = try { Color.parseColor(task.taskColor) } catch (e: Exception) { Color.LTGRAY }
                val alphaFactor = if (task.completed) 0.5f else 1f
                bubblePaint.alpha = (bubblePaint.alpha * alphaFactor).toInt()
                canvas.drawRoundRect(bubbleRect, 8f * sdp, 8f * sdp, bubblePaint)
                
                val innerX = taskStartX + 16f * sdp
                var cy = taskY + 16f * sdp
                
                // Project label - NType82
                textPaint.textSize = projSize
                textPaint.typeface = displayTypeface
                textPaint.color = Color.argb((153 * alphaFactor).toInt(), 0, 0, 0)
                canvas.drawText(projName, innerX, cy - textPaint.ascent(), textPaint)
                
                // Deadline time on top right
                val taskDueLocalDate = task.dueAtMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                if (taskDueLocalDate == date && task.dueAtMillis != null) {
                    val dueTimeStr = java.time.Instant.ofEpochMilli(task.dueAtMillis!!).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US))
                    textPaint.textSize = projSize
                    textPaint.typeface = displayTypeface
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(dueTimeStr, taskStartX + taskWidth - 16f * sdp, cy - textPaint.ascent(), textPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                }
                
                cy += projLineH + 8f * sdp
                
                // Title - NType82
                textPaint.textSize = titleSize
                textPaint.typeface = displayTypeface
                textPaint.color = Color.argb((255 * alphaFactor).toInt(), 0, 0, 0)
                val titleStr = ellipsize(task.title, maxTextWidth, titleSize, displayTypeface)
                canvas.drawText(titleStr, innerX, cy - textPaint.ascent(), textPaint)
                cy += titleLineH
                
                // Description - NType82, full multi-line
                if (descLines.isNotEmpty()) {
                    cy += 4f * sdp
                    textPaint.textSize = descSize
                    textPaint.typeface = displayTypeface
                    textPaint.color = Color.argb((204 * alphaFactor).toInt(), 0, 0, 0)
                    descLines.forEach { line ->
                        canvas.drawText(line, innerX, cy - textPaint.ascent(), textPaint)
                        cy += descLineH
                    }
                }
                
                taskY += contentH + 12f * sdp
            }
            taskBottomY = taskY
        }
        
        linePaint.color = Color.DKGRAY
        linePaint.strokeWidth = 1f * sdp
        canvas.drawLine(railCenterX, lineStartY, railCenterX, kotlin.math.max(lineStartY, taskBottomY), linePaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawSignalLine(canvas: Canvas, rect: RectF, element: WallpaperElement, tasks: List<AgendaTask>) {
        val displayTasks = tasks.sortedBy { it.completed }.take(5)
        val textSize = element.style.textSizePx * element.scale
        val railX = rect.left + rect.width() * 0.10f
        val top = rect.top + textSize * 0.55f
        val bottom = rect.bottom - textSize * 0.55f
        val signal = element.style.accentColor.toArgb()
        val dimSignal = Color.rgb(90, 33, 28)
        val primary = element.style.color.toArgb()
        val secondary = Color.rgb(156, 156, 159)

        linePaint.strokeWidth = max(2f, textSize * 0.07f)
        linePaint.color = dimSignal
        canvas.drawLine(railX, top, railX, bottom, linePaint)

        if (displayTasks.isEmpty()) {
            canvas.drawCircle(railX, rect.centerY(), max(6f, textSize * 0.19f), linePaint)
            drawCanvasText(canvas, "ALL CLEAR", railX + textSize * 1.2f, rect.centerY() + textSize * 0.28f, textSize * 0.78f, bodyTypeface, secondary)
            return
        }

        val spacing = if (displayTasks.size == 1) 0f else (bottom - top) / (displayTasks.size - 1)
        displayTasks.forEachIndexed { index, task ->
            val y = if (displayTasks.size == 1) rect.centerY() else top + index * spacing
            val radius = max(5f, textSize * 0.17f)
            if (index == 0) {
                linePaint.color = Color.argb(72, 232, 54, 43)
                linePaint.strokeWidth = radius * 1.8f
                canvas.drawCircle(railX, y, radius * 1.55f, linePaint)
                textPaint.color = signal
                textPaint.style = Paint.Style.FILL
                canvas.drawCircle(railX, y, radius, textPaint)
            } else {
                linePaint.color = dimSignal
                linePaint.strokeWidth = max(2f, textSize * 0.06f)
                canvas.drawCircle(railX, y, radius, linePaint)
            }

            val labelX = railX + textSize * 1.25f
            val alphaFactor = if (task.completed) 0.5f else 1f
            val aPrimary = Color.argb((Color.alpha(primary) * alphaFactor).toInt(), Color.red(primary), Color.green(primary), Color.blue(primary))
            val aSecondary = Color.argb((Color.alpha(secondary) * alphaFactor).toInt(), Color.red(secondary), Color.green(secondary), Color.blue(secondary))
            drawCanvasText(canvas, ellipsize(task.title, rect.right - labelX, textSize * 0.88f, bodyTypeface), labelX, y - textSize * 0.22f, textSize * 0.88f, bodyTypeface, aPrimary)
            val meta = task.notes.ifBlank { task.project }.uppercase(Locale.US)
            drawCanvasText(canvas, ellipsize(meta, rect.right - labelX, textSize * 0.58f, monoTypeface), labelX, y + textSize * 0.58f, textSize * 0.58f, monoTypeface, aSecondary)
        }
    }

    private fun drawCompactList(canvas: Canvas, rect: RectF, element: WallpaperElement, tasks: List<AgendaTask>) {
        val textSize = element.style.textSizePx * element.scale
        val lineHeight = max(20f, textSize * element.style.lineHeightMultiplier)
        val list = tasks.sortedBy { it.completed }.ifEmpty { listOf(AgendaTask(title = "ALL CLEAR")) }
        list.take(max(1, (rect.height() / lineHeight).toInt())).forEachIndexed { index, task ->
            val color = element.style.color.toArgb()
            val aColor = if (task.completed) Color.argb((Color.alpha(color) * 0.5f).toInt(), Color.red(color), Color.green(color), Color.blue(color)) else color
            drawCanvasText(
                canvas,
                ellipsize(task.title, rect.width(), textSize, bodyTypeface),
                rect.left,
                rect.top + textSize + index * lineHeight,
                textSize,
                bodyTypeface,
                aColor,
            )
        }
    }

    private fun drawTextBlock(canvas: Canvas, rect: RectF, text: String, element: WallpaperElement, typeface: Typeface) {
        val x = when (element.style.alignment) {
            TextAlignment.START -> rect.left
            TextAlignment.CENTER -> rect.centerX()
            TextAlignment.END -> rect.right
        }
        textPaint.textAlign = when (element.style.alignment) {
            TextAlignment.START -> Paint.Align.LEFT
            TextAlignment.CENTER -> Paint.Align.CENTER
            TextAlignment.END -> Paint.Align.RIGHT
        }
        textPaint.typeface = typeface
        textPaint.textSize = element.style.textSizePx * element.scale
        textPaint.color = element.style.color.toArgb()
        canvas.drawText(text, x, rect.top - textPaint.fontMetrics.ascent, textPaint)
    }

    private fun drawRule(canvas: Canvas, rect: RectF, element: WallpaperElement) {
        linePaint.color = element.style.accentColor.toArgb()
        linePaint.strokeWidth = max(2f, rect.height())
        canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), linePaint)
    }

    private fun drawSelection(canvas: Canvas, width: Int, height: Int, element: WallpaperElement) {
        val rect = RectF(
            element.bounds.x * width,
            element.bounds.y * height,
            (element.bounds.x + element.bounds.width) * width,
            (element.bounds.y + element.bounds.height) * height,
        )
        linePaint.color = ElementColor.RED.toArgb()
        linePaint.strokeWidth = 2f
        canvas.drawRect(rect, linePaint)
    }

    private fun drawCanvasText(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, typeface: Typeface, color: Int) {
        textPaint.typeface = typeface
        textPaint.textSize = size
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = color
        textPaint.letterSpacing = 0f
        canvas.drawText(text, x, baseline, textPaint)
    }

    private fun ellipsize(text: String, maxWidth: Float, size: Float, typeface: Typeface): String {
        textPaint.typeface = typeface
        textPaint.textSize = size
        if (textPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && textPaint.measureText(text.substring(0, end) + "...") > maxWidth) end--
        return text.substring(0, end).trimEnd() + "..."
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in words) {
            val test = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(test) <= maxWidth) {
                currentLine = StringBuilder(test)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}
