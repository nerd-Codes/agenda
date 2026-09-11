# Agenda

A local-first Android task app where your wallpaper is the UI.

Agenda lets you manage daily tasks and then position a live task list directly on your lock screen wallpaper — draggable, scalable, and always in sync with what's actually left to do.

---

## What it does

- **Task management** — Create, edit, complete, and delete tasks. Assign a project, priority, deadline (date + time), notes, color, and file attachments.
- **Live wallpaper** — A wallpaper service renders today's tasks over your phone's background. Completed tasks dim. Deadlines show their time on the due day.
- **Editor** — Position and scale the task group directly on a preview of your wallpaper. Drag to move, pinch to scale, tap Save to apply.
- **Home screen** — Greets you with a different one-liner every launch. Shows today's tasks sorted by completion, with a tap-to-complete checkbox on each card.
- **All Tasks** — Browse tasks by date with a horizontal date strip. Task cards show a deadline time badge on the day they're due.
- **Settings** — Set your name, open Android's live wallpaper picker.

---

## Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Database | Room |
| Preferences | DataStore |
| Live Wallpaper | `WallpaperService` + native `Canvas` renderer |
| Min SDK | 29 (Android 10) |
| Typography | NType 82 (display), system sans-serif (body) |

---

## Screens

### Home
The daily overview. Tasks for today listed as cards showing project label, title, description, due date, priority pill, and a circular completion checkbox. Completed tasks dim to 50% and sort to the bottom with a slide animation. A randomised greeting line changes every launch.

### All Tasks
A full date-scrolling task browser. Tap any date in the strip or the month header to jump. Task cards show a `h:mm a` deadline badge in the top-right corner — but only on the exact due day.

### Editor (Preview)
A full-screen wallpaper canvas. Use your phone's wallpaper as the background or set a solid/image background. Drag the task group to reposition, pinch to scale. Tap **Save** to write the layout to the live wallpaper service. Both the editor preview and the live wallpaper use the same renderer, so what you see is what you get.

### Create / Edit Task
Single scrollable form. Fields: name, notes, project, start date, deadline (date + time picker), priority (1–4), color picker, file attachments. Attachments are copied to internal storage and open in the system's default viewer when tapped.

### Settings
Name input (shown in the Home greeting), live wallpaper setter button.

---

## Architecture

```
MainActivity.kt           — all Compose screens and navigation
AgendaViewModel           — UI state, business logic, DataStore writes
AgendaRepository          — combines Room + DataStore flows
AgendaDatabase / TaskDao  — Room database for tasks
AgendaPreferences         — DataStore for layout, name, background
WallpaperRenderer.kt      — native Canvas renderer (shared by editor + service)
AgendaWallpaperService    — Android WallpaperService consuming the renderer
```

The editor preview and the live wallpaper surface both call `WallpaperRenderer.render()` with the same composition and task list, so they are always pixel-identical.

---

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Install directly
./gradlew installDebug
```

Requires Android Studio Ladybug or newer, JDK 17+.

---

## Design principles

- **Local-first** — no accounts, no sync, no network permissions.
- **One font identity** — NType 82 for all display text on task cards and the wallpaper. Sans-serif for metadata labels.
- **True black** — `#000000` background throughout. Single red accent (`#E8362B`).
- **Calm signal** — the wallpaper shows today's incomplete tasks (completed ones dim, not disappear). No clocks, widgets, or decorations compete with the task list.
- **Direct manipulation** — drag and pinch in the editor, not sliders.

---

## What's not included

- Cloud sync, OAuth, or any remote API
- Calendar integration
- Team / shared workspaces
- iOS, web, or desktop client

---

## License

MIT
