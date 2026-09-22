# Lumen Launcher — guide for Claude

Lumen is an Android home-screen launcher built with Jetpack Compose. It combines a glassy home experience, horizontal “Spaces,” voice (“Hey Lumen”), inbox/meeting awareness, todos/capture, and a static marketing site in `site/`.

## Repository layout

| Path | Purpose |
|------|---------|
| `app/src/main/java/com/lumen/launcher/ui/` | Compose UI: home, drawer, sheets, flow/feed/todo pages |
| `app/src/main/java/com/lumen/launcher/vm/` | `LauncherViewModel` and UI state |
| `app/src/main/java/com/lumen/launcher/data/` | Preferences, repositories, Spaces, wallpapers, LLM helpers |
| `app/src/main/java/com/lumen/launcher/voice/` | Voice engine, recognition, intent parsing |
| `app/src/main/java/com/lumen/launcher/inbox/` | Notification listener, digest, meetings, WhatsApp |
| `app/src/main/java/com/lumen/launcher/search/` | Fuzzy search, calculator, voice match |
| `app/src/main/java/com/lumen/launcher/alarm/` | Alarms and scheduling |
| `app/src/test/java/` | Unit tests (JUnit + Truth) |
| `site/` | Static landing page (HTML/CSS/JS) for GitHub Pages |

Entry points: `LauncherActivity.kt`, `LumenApp.kt`, root composable `ui/LauncherRoot.kt`.

## Tech stack

- Kotlin 17, `compileSdk` / `targetSdk` 35, `minSdk` 26
- Jetpack Compose (Material 3), ViewModel, DataStore preferences
- Single module Gradle project: root `build.gradle.kts`, app `app/build.gradle.kts`
- Optional Gemini API for AI fallbacks (see secrets below)

## Architecture notes

- **State:** `LauncherViewModel` owns most launcher state; UI reads `LauncherUiState` and calls ViewModel methods for actions.
- **Preferences:** `LauncherPreferences` (DataStore) persists user settings, Spaces, dock, gestures, icon skin, glass depth, etc.
- **Spaces:** Contextual home modes (`SpaceKind`, `SpaceSense`, `SpaceWallpaper`) drive wallpaper and focus behavior.
- **Voice:** Deterministic parsers first; `AiIntentFallback` / `LlmClient` used when needed. Do not break offline-first behavior without explicit intent.
- **UI patterns:** Shared primitives in `Components.kt`, sheets in `Sheets.kt` / `DrawerSheet.kt`, theme in `ui/theme/`.

## Build and test

From repo root (Windows or Unix):

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Install debug APK after assemble: `app/build/outputs/apk/debug/`.

## Secrets and local config

- `local.properties` is gitignored. For Gemini-backed features, add:
  ```
  GEMINI_API_KEY=your_key_here
  ```
- Keys are injected at build time into `BuildConfig.GEMINI_API_KEY` in `app/build.gradle.kts`. Never commit API keys.

## Conventions for changes

1. **Minimize scope** — match existing naming, file placement, and Compose style in neighboring code.
2. **No drive-by refactors** — especially in `LauncherViewModel.kt` (large file); touch only what the task needs.
3. **Tests** — add or update unit tests under `app/src/test/` for non-trivial logic in `data/`, `search/`, and `voice/intent/`.
4. **Do not commit** — `tmp/`, `app/build/`, keystores, or `local.properties`.
5. **Marketing site** — keep `site/` self-contained; assets live under `site/img/`.

## Git / GitHub

- Remote: `https://github.com/srican1982/lumen-launcher.git`
- Active feature branch is often `Site` (includes `site/` and recent UI work); `main` is the default integration branch.
- Commit messages: short imperative sentence describing *why*, consistent with existing history.

## Product vocabulary

- **Space** — themed home context (e.g. work, personal) with wallpaper and suggested apps.
- **Flow** — horizontal page of glanceable cards and actions.
- **Drawer** — app list with search and alphabet rail.
- **Capture / Todo / Daily review** — lightweight productivity layer on top of the launcher.
