package app.agenda.wallpaper

import app.agenda.wallpaper.model.DeviceCanvasProfile
import app.agenda.wallpaper.model.ElementType
import app.agenda.wallpaper.model.NormalizedRect
import app.agenda.wallpaper.model.PixelRect
import app.agenda.wallpaper.model.WallpaperElement
import app.agenda.wallpaper.model.contains
import app.agenda.wallpaper.model.minimalComposition
import app.agenda.wallpaper.model.scaledProportionally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionModelTest {
    @Test
    fun pixelConversionRoundTripsAgainstDetectedCanvas() {
        val profile = DeviceCanvasProfile(widthPx = 1080, heightPx = 2400)
        val normalized = NormalizedRect.fromPixels(PixelRect(108, 240, 540, 480), profile)

        assertEquals(0.1f, normalized.x, 0.001f)
        assertEquals(0.1f, normalized.y, 0.001f)
        assertEquals(PixelRect(108, 240, 540, 480), normalized.toPixelRect(profile))
    }

    @Test
    fun translatedBoundsStayInsideCanvas() {
        val moved = NormalizedRect(0.9f, 0.9f, 0.2f, 0.2f).translated(0.3f, 0.3f)

        assertTrue(moved.x <= 0.8f)
        assertTrue(moved.y <= 0.8f)
    }

    @Test
    fun minimalTemplateUsesProvidedCanvas() {
        val profile = DeviceCanvasProfile(widthPx = 1440, heightPx = 3120)
        val composition = minimalComposition(profile)

        assertEquals(profile, composition.baseCanvas)
        assertTrue(composition.elements.any { it.id == "task-list" })
        assertTrue(composition.elements.any { it.id == "clock" })
    }

    @Test
    fun proportionalScalingKeepsCenterAndScalesTypography() {
        val element = WallpaperElement(
            id = "task-list",
            type = ElementType.TASK_LIST,
            bounds = NormalizedRect(0.2f, 0.3f, 0.2f, 0.1f),
            scale = 1f,
        )

        val scaled = element.scaledProportionally(2f)

        assertEquals(0.4f, scaled.bounds.width, 0.001f)
        assertEquals(0.2f, scaled.bounds.height, 0.001f)
        assertEquals(0.1f, scaled.bounds.x, 0.001f)
        assertEquals(0.25f, scaled.bounds.y, 0.001f)
        assertEquals(2f, scaled.scale, 0.001f)
    }

    @Test
    fun elementHitTestingUsesNormalizedBounds() {
        val element = WallpaperElement(
            id = "clock",
            type = ElementType.CLOCK,
            bounds = NormalizedRect(0.2f, 0.3f, 0.2f, 0.1f),
        )

        assertTrue(element.contains(0.3f, 0.35f))
        assertTrue(!element.contains(0.1f, 0.35f))
    }
}
