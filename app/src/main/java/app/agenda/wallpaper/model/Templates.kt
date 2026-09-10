package app.agenda.wallpaper.model

fun minimalComposition(profile: DeviceCanvasProfile): WallpaperComposition = taskListOnlyComposition(profile)

fun taskListOnlyComposition(
    profile: DeviceCanvasProfile,
    existing: WallpaperComposition? = null,
): WallpaperComposition {
    val previousTaskList = existing?.elements?.firstOrNull { it.type == ElementType.TASK_LIST }
    val taskList = previousTaskList?.copy(
        id = "task-list",
        type = ElementType.TASK_LIST,
        style = previousTaskList.style.copy(
            textSizePx = previousTaskList.style.textSizePx.coerceAtLeast(profile.heightPx * 0.014f),
            taskVisualStyle = TaskVisualStyle.SIGNAL_LINE,
            color = ElementColor.OFF_WHITE,
            accentColor = ElementColor.RED,
        ),
    ) ?: WallpaperElement(
        id = "task-list",
        type = ElementType.TASK_LIST,
        bounds = NormalizedRect(0.10f, 0.22f, 0.80f, 0.48f),
        style = ElementStyle(
            textSizePx = profile.heightPx * 0.017f,
            lineHeightMultiplier = 1.35f,
            color = ElementColor.OFF_WHITE,
            accentColor = ElementColor.RED,
            taskVisualStyle = TaskVisualStyle.SIGNAL_LINE,
        ),
    )

    return WallpaperComposition(
        id = existing?.id ?: "default",
        name = "Signal Line",
        baseCanvas = profile,
        background = BackgroundConfig(color = ElementColor.BLACK),
        showGridOnWallpaper = false,
        elements = listOf(taskList),
    )
}
