package app.agenda.wallpaper.render

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import app.agenda.wallpaper.model.AgendaTask
import app.agenda.wallpaper.model.WallpaperComposition
import app.agenda.wallpaper.model.contains
import app.agenda.wallpaper.model.minimalComposition
import app.agenda.wallpaper.model.scaledProportionally

class AgendaPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val renderer = WallpaperRenderer(context)
    var composition: WallpaperComposition = minimalComposition(app.agenda.wallpaper.model.DeviceCanvasProfile())
        set(value) {
            field = value
            invalidate()
        }
    var tasks: List<AgendaTask> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var selectedElementId: String? = null
        set(value) {
            field = value
            invalidate()
        }
    var editorOverlays: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var onElementSelected: ((String?) -> Unit)? = null
    var onCompositionChanged: ((WallpaperComposition) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val selected = composition.element(selectedElementId ?: return false) ?: return false
            val updated = composition.updateElement(selected.scaledProportionally(detector.scaleFactor))
            composition = updated
            onCompositionChanged?.invoke(updated)
            return true
        }
    })

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.render(
            canvas = canvas,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            composition = composition,
            tasks = tasks,
            selectedElementId = selectedElementId,
            editorOverlays = editorOverlays,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
                val hit = hitTest(event.x, event.y)
                selectedElementId = hit
                onElementSelected?.invoke(hit)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val selected = selectedElementId?.let { composition.element(it) }
                    if (selected != null) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        val updated = composition.updateElement(
                            selected.copy(
                                bounds = selected.bounds.translated(
                                    dx / width.coerceAtLeast(1),
                                    dy / height.coerceAtLeast(1),
                                ),
                            ),
                        )
                        composition = updated
                        onCompositionChanged?.invoke(updated)
                    }
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitTest(x: Float, y: Float): String? {
        val normalizedX = x / width.coerceAtLeast(1)
        val normalizedY = y / height.coerceAtLeast(1)
        return composition.elements.asReversed().firstOrNull { element ->
            element.contains(normalizedX, normalizedY)
        }?.id
    }
}
