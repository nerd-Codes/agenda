package app.agenda.wallpaper.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class DeviceCanvasProfile(
    val widthPx: Int = 1080,
    val heightPx: Int = 2400,
    val density: Float = 1f,
    val safeTopPx: Int = 160,
    val safeBottomPx: Int = 160,
    val lastDetectedAt: Long = 0L,
) {
    val aspectRatio: Float
        get() = widthPx.toFloat() / max(1, heightPx).toFloat()
}

@Serializable
data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun toPixelRect(profile: DeviceCanvasProfile): PixelRect = PixelRect(
        x = (x * profile.widthPx).roundToInt(),
        y = (y * profile.heightPx).roundToInt(),
        width = (width * profile.widthPx).roundToInt(),
        height = (height * profile.heightPx).roundToInt(),
    )

    fun translated(dx: Float, dy: Float): NormalizedRect = copy(
        x = (x + dx).coerceIn(0f, 1f - width.coerceAtMost(1f)),
        y = (y + dy).coerceIn(0f, 1f - height.coerceAtMost(1f)),
    )

    fun scaled(factor: Float): NormalizedRect {
        return scaledAroundCenter(factor)
    }

    fun scaledAroundCenter(factor: Float): NormalizedRect {
        val nextWidth = (width * factor).coerceIn(0.04f, 1f)
        val nextHeight = (height * factor).coerceIn(0.02f, 1f)
        val centerX = x + width / 2f
        val centerY = y + height / 2f
        return copy(
            x = (centerX - nextWidth / 2f).coerceIn(0f, 1f - nextWidth),
            y = (centerY - nextHeight / 2f).coerceIn(0f, 1f - nextHeight),
            width = nextWidth,
            height = nextHeight,
        )
    }

    companion object {
        fun fromPixels(rect: PixelRect, profile: DeviceCanvasProfile): NormalizedRect = NormalizedRect(
            x = rect.x.toFloat() / max(1, profile.widthPx),
            y = rect.y.toFloat() / max(1, profile.heightPx),
            width = rect.width.toFloat() / max(1, profile.widthPx),
            height = rect.height.toFloat() / max(1, profile.heightPx),
        )
    }
}

data class PixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

@Serializable
enum class ElementType {
    CLOCK,
    DATE,
    DAY,
    TASK_LIST,
    TASK_COUNT,
    PROGRESS,
    PROJECT,
    CUSTOM_TEXT,
    LINE,
    GRID,
}

@Serializable
enum class ElementColor {
    WHITE,
    OFF_WHITE,
    RED,
    DARK_RED,
    BLACK,
}

@Serializable
enum class TaskVisualStyle {
    SIMPLE,
    NUMBERED,
    TIMELINE,
    SIGNAL_LINE,
    TECHNICAL,
    COMPACT,
}

@Serializable
data class ElementStyle(
    val textSizePx: Float = 48f,
    val letterSpacingEm: Float = 0.04f,
    val lineHeightMultiplier: Float = 1.18f,
    val color: ElementColor = ElementColor.OFF_WHITE,
    val accentColor: ElementColor = ElementColor.RED,
    val alignment: TextAlignment = TextAlignment.START,
    val taskVisualStyle: TaskVisualStyle = TaskVisualStyle.SIMPLE,
    val maxLines: Int = 2,
    val label: String = "",
)

@Serializable
enum class TextAlignment {
    START,
    CENTER,
    END,
}

@Serializable
data class WallpaperElement(
    val id: String,
    val type: ElementType,
    val bounds: NormalizedRect,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val supportsRotation: Boolean = false,
    val visible: Boolean = true,
    val style: ElementStyle = ElementStyle(),
)

fun WallpaperElement.scaledProportionally(factor: Float): WallpaperElement = copy(
    bounds = bounds.scaledAroundCenter(factor),
    scale = (scale * factor).coerceIn(0.35f, 4f),
)

fun WallpaperElement.contains(normalizedX: Float, normalizedY: Float): Boolean =
    visible && normalizedX in bounds.x..(bounds.x + bounds.width) &&
        normalizedY in bounds.y..(bounds.y + bounds.height)

@Serializable
data class WallpaperComposition(
    val id: String = "default",
    val name: String = "Minimal",
    val baseCanvas: DeviceCanvasProfile = DeviceCanvasProfile(),
    val background: BackgroundConfig = BackgroundConfig(),
    val showGridOnWallpaper: Boolean = false,
    val elements: List<WallpaperElement> = emptyList(),
) {
    fun element(id: String): WallpaperElement? = elements.firstOrNull { it.id == id }

    fun updateElement(updated: WallpaperElement): WallpaperComposition = copy(
        elements = elements.map { if (it.id == updated.id) updated else it },
    )
}

@Serializable
data class BackgroundConfig(
    val mode: BackgroundMode = BackgroundMode.SOLID,
    val color: ElementColor = ElementColor.BLACK,
    val grain: Boolean = false,
    val scanlines: Boolean = false,
)

@Serializable
enum class BackgroundMode {
    SOLID,
}

enum class TaskFilter {
    TODAY,
    OVERDUE,
    TODAY_AND_OVERDUE,
    UPCOMING,
    ALL,
}

enum class TaskSort {
    MANUAL,
    DUE_DATE,
    PRIORITY,
    PROJECT,
}

data class AgendaTask(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val completed: Boolean = false,
    val dueAtMillis: Long? = null,
    val startAtMillis: Long? = null,
    val endAtMillis: Long? = null,
    val priority: Int = 1,
    val project: String = "",
    val projectIcon: String = "Folder",
    val taskColor: String = "Purple",
    val attachments: String = "",
    val sortOrder: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
