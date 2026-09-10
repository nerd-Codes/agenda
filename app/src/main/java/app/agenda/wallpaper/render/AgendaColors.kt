package app.agenda.wallpaper.render

import android.graphics.Color
import app.agenda.wallpaper.model.ElementColor

fun ElementColor.toArgb(): Int = when (this) {
    ElementColor.BLACK -> Color.BLACK
    ElementColor.WHITE -> Color.rgb(245, 244, 241)
    ElementColor.OFF_WHITE -> Color.rgb(245, 244, 241)
    ElementColor.RED -> Color.rgb(232, 54, 43)
    ElementColor.DARK_RED -> Color.rgb(90, 33, 28)
}
