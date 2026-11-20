# AGENTS.md

Android app (Kotlin, View-based Activities + ViewBinding) for downloading TikTok/Douyin videos without watermark, with optional Vietnamese subtitles (`.srt`) and TTS narration.

## Build & verify

- Single module `:app`, package `com.example.tt`; compileSdk/targetSdk 35, minSdk 26, Java 11, AGP 8.7.3, Kotlin 2.0.21.
- **There is no Gradle wrapper.** CI uses system Gradle 8.10.2 (`gradle ...`, never `./gradlew`).
- This workspace has no JDK or Android SDK, so Gradle cannot run locally. Push to `main` and verify in CI instead.
  - Check: `https://api.github.com/repos/trinhxuantanh2so4-jpg/tt/commits/<sha>/check-runs`. CI logs are not reachable from here; on failure read `/check-runs/<id>/annotations` (the workflow emits `::error::` annotations for build/test failures).
  - Resource-only syntax check works locally: `aapt2 compile --dir app/src/main/res -o app/build/tmpcheck/aapt2.zip` (binary at `C:\Users\Admin\AppData\Local\Temp\opencode\aapt2\ext\aapt2.exe`).
- Unit tests: `gradle testDebugUnitTest`. One class: `gradle testDebugUnitTest --tests "com.example.tt.subtitle.WavIoTest"`.
- Every push to `main` builds the debug APK and overwrites `app-debug.apk` on the fixed release tag `v1.0.0`.
- On PowerShell, commit with `git commit -F <file>`; inline `-m` breaks on Vietnamese text/parentheses.

## Architecture

- `BaseDownloadActivity` owns all shared download logic: URL parsing, analyze, SAF save, progress/speed, SQLite insert, Firestore counter, clipboard. `MainActivity` (TikTok) and `DouyinActivity` only set `platformFilter`/`navItemId` and override hooks `onVideoCardShown`, `onDownloadCompleted`, `onWrongPlatform`. Always call `setupDownloadForm(...)` first; a `false` return means it is already navigating to login.
- `VideoResolver.resolve()` is the single link entrypoint: TikTok → `ApiClient` (tikwm.com), Douyin → `douyin/DouyinResolver` (parses the `iesdouyin.com` share page, `_ROUTER_DATA`).
- Layouts: `activity_main.xml` and `activity_douyin.xml` both include `part_download_form.xml` as `form`. The Douyin-only block `part_subtitle_options.xml` is included as `subtitleOptions` and stays hidden on the TikTok tab.
- Bottom nav has exactly 4 items (TikTok, Douyin, History, Profile); Trending opens from the toolbar menu (`menu_main.xml`). Screens outside those 4 pass `R.id.nav_none` (defined in `values/ids.xml`) to `setupBottomNav`.
- SQLite (`data/DatabaseHelper.kt`) is at version 3. For schema changes, bump `DATABASE_VERSION` and add columns in `onUpgrade` via `addColumnIfMissing`; don't recreate tables.
- Subtitle pipeline: `AudioExtractor` (PCM mono 16 kHz) → `AsrEngine` (Moonshine; model downloads at runtime) → `Translator` (ML Kit) → `CueBuilder`/`SrtWriter` (`.srt` under `filesDir/subtitles`).
- Narration pipeline: `SystemTtsEngine` (vi-VN) → `NarrationTimeline` (place cues at `startMs`, mix 20% background, normalize) → `WavIo` (24 kHz WAV) → `NarrationMuxer` (Media3 Transformer 1.6.1, muted video + WAV). Narration must only start when the user taps "Bắt đầu xử lý", never automatically on download completion.

## Conventions & gotchas

- All UI text is Vietnamese in `res/values/strings.xml`; there are no `values-*` translation folders.
- `app/google-services.json` is a committed demo config. Do not delete it; Firebase errors fall back to offline SQLite auth in `firebase/AuthRepository`.
- Unit tests must stay pure JVM: `WavIo` and `NarrationTimeline` are deliberately Android-free. Don't add Android imports to helpers under `app/src/test`.
- Media3 Transformer requires `@androidx.annotation.OptIn(markerClass = [UnstableApi::class])`, and `Transformer.start(...)` must be called on the main thread.
- APK is restricted to `arm64-v8a` + `x86_64` (`abiFilters`); Moonshine native libs make it ~81 MB.
- ML Kit needs Google Play services and TTS needs a `vi-VN` system voice. Report their absence with a clear message; never crash.
- Full reference: `README.md` (API, Firebase, SQLite details) and `docs/PLAN_TIKTOK_DOUYIN.md` (roadmap and current status).
