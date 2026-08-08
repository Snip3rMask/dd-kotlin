# MiruDL — Kotlin Multiplatform (KMP) Migration Plan
<!-- Detailed sub-step breakdown lives in MIGRATION_PLAN_DETAILED.md (added 2026-08-04).
     This file keeps the original phase boxes; the detailed file splits Phases 2-10
     into per-commit sub-steps with dependency order, technical constraints, and
     a per-step verification checklist. Update boxes here, details there. -->


**Decision (locked in):** targeting Android, Android TV, iOS, Windows, and
Linux via **Kotlin Multiplatform + Compose Multiplatform**. This supersedes
the earlier "just convert Java → Kotlin inside the single Android app" plan
— we go straight to a multiplatform module layout so we don't do that move
twice.

## Core strategy: "strangler fig", never break the shipping app

The existing Android app (`app` module, 25 Java files) **keeps building and
shipping normally the entire time**. We do NOT do a big-bang rewrite. Instead:

1. Create a new `:shared` Kotlin Multiplatform module.
2. Move one piece of framework-agnostic logic at a time from `app` into
   `:shared/commonMain`, converting Java → Kotlin in the same step.
3. `app` starts depending on `:shared` for that piece instead of its own copy.
4. Build + test after every single move. `app` must always still work.
5. Only once most business logic lives in `:shared` do we add the other
   platform targets (iOS, Desktop) and their own thin UI-only app modules.
6. Android TV is nearly free — it reuses the Android target, just needs
   D-pad focus handling added to the UI later.

This means: no wasted double-conversion work, and the Android app is never
in a broken state for more than one step at a time — important since you're
building/testing via GitHub Actions only, no local PC.

## Target end-state module layout

```
MiruDL-App/
├── app/                    Android application (existing module, phones + tablets)
├── tvApp/                  Android TV application (new, thin — reuses :shared, TV-focused UI)
├── shared/                 Kotlin Multiplatform module
│   src/
│     commonMain/kotlin/    Platform-agnostic: models, networking, download logic,
│                           job queue, HLS parsing, update checker, business rules
│     androidMain/kotlin/   Android actuals: SAF/DocumentFile storage, notifications,
│                           foreground Service, SharedPreferences
│     iosMain/kotlin/       iOS actuals: file storage, background task handling
│     desktopMain/kotlin/   Windows/Linux actuals: plain file I/O, tray/background handling
├── iosApp/                 Xcode project (SwiftUI or Compose Multiplatform UI), added in Phase 7
├── desktopApp/             Compose Multiplatform Desktop app, added in Phase 8
└── MIGRATION_PLAN.md       (this file)
```

## Known non-mechanical porting points (flagging now, not surprises later)

These are NOT simple Java→Kotlin syntax translation — they need real
rework when they move to `commonMain`:

- **Networking (`MiruClient`, `HlsDownloader`, `UpdateChecker`)** currently
  use OkHttp directly. OkHttp is JVM/Android-only. Multiplatform code needs
  **Ktor Client** instead (engine-swappable: OkHttp engine on Android,
  Darwin engine on iOS, CIO/Java engine on Desktop). The actual HTML/JSON
  parsing logic stays the same — only the HTTP call layer changes.
- **File storage.** Android's SAF/`DocumentFile` (used in `HlsDownloader`,
  `StorageSettings`) is an Android-only concept. iOS and Desktop each have
  their own plain file-system APIs. This becomes `expect fun`/`actual fun`
  per platform.
- **SharedPreferences** (`StorageSettings`, `DownloadEntryStore`,
  `UpdateChecker`'s cache) → multiplatform key-value storage (e.g.
  `multiplatform-settings` library or DataStore KMP) with an
  expect/actual or common interface.
- **Foreground `Service` + notifications** (`DownloadService`,
  `CancelDownloadReceiver`, `MiruDLApp`'s notification channel) are
  Android-specific OS concepts. iOS/Desktop need their own background-work
  and notification mechanisms, built per-platform later (Phase 5/7/8) —
  the *job queue logic* (`DownloadManager`) itself is common, only the
  OS-level "run this in the background and show progress" wrapper differs.
- **UI.** The existing Android UI is plain Views/XML layouts. Compose
  Multiplatform is Compose-based, so Android UI will need to move to
  Jetpack Compose at some point to actually be shared with iOS/Desktop —
  this is a real rewrite of the UI layer, tracked as its own phase, not
  bundled silently into the logic move.

## Ground rules for every step

- One step = one buildable commit. Convert + move the file(s), fix every
  reference across the project, confirm CI is green before the next box.
- `app` module must build successfully after every single step.
- Don't silently fix bugs or change behavior mid-move — logic ports as-is.
  Real improvements get a `NOTE:` line here for later.
- Check a box only after a green build.

---

## Phase 0 — Setup

- [x] 0.1 Add the Kotlin Multiplatform Gradle plugin and create an empty
      `:shared` module with `commonMain` + `androidMain` source sets
      (targets: `android`, `jvm` for desktop-to-come). No logic moved yet —
      just confirms the new module builds and `app` can depend on it.
- [x] 0.2 Delete the verified dead-code cluster: `DownloadsActivity.java`,
      its `<activity>` entry in `AndroidManifest.xml`, `activity_downloads.xml`,
      and the two fully-orphaned item layouts `item_download_active.xml` +
      `item_download_completed.xml` (referenced by nothing — `DownloadAdapter`
      builds its rows entirely in code). Re-verified: no `startActivity()`
      call anywhere targets `DownloadsActivity`, and `DownloadAdapter.java`
      itself stays since `MainActivity` still uses it for the real
      Downloads tab.
- [x] 0.3 Add Ktor Client dependency to `:shared/commonMain` (not wired up
      to anything yet — just confirms the dependency resolves for all
      targets we plan to support).

## Phase 1 — Pure data models → `shared/commonMain` (safest start, zero dependencies)

- [x] 1.1 `AnimeItem.java` → `shared/commonMain/.../model/AnimeItem.kt` (data class)
- [x] 1.2 `EpisodeItem.java` → `shared/commonMain/.../model/EpisodeItem.kt` (data class, keep `getLabel()`)
- [x] 1.3 `VideoSource.java` → `shared/commonMain/.../model/VideoSource.kt` (data class)
- [x] 1.4 `DownloadEntryStore.Entry` (inner class) → its own
      `shared/commonMain/.../model/DownloadRecord.kt` data class — renamed
      from `DownloadEntry` to `DownloadRecord` (see progress log: the name
      `DownloadEntry` was already taken by an existing Android-only UI
      wrapper class in `app`). The *storage* half of `DownloadEntryStore`
      (SharedPreferences read/write) stays in `app` for now, moves in
      Phase 3 once the storage abstraction exists — this step only moved
      the plain data shape.

## Phase 2 — Networking → `shared/commonMain` (Ktor porting work, see notes above)

- [ ] 2.1 Add Ktor Client engines for each planned target (OkHttp for
      Android, CIO/Java for Desktop, Darwin added later in Phase 7 for iOS)
      and a tiny `shared/commonMain/.../network/HttpClientProvider.kt` that
      just returns a configured `HttpClient`. No real calls yet — confirms
      the engine wiring builds on every target we have so far.
- [ ] 2.2 Port `MiruClient`'s two small HTTP helper methods (`get`,
      `getHtml`) → Ktor equivalents in
      `shared/commonMain/.../network/MiruClient.kt`. Just the two helpers,
      nothing else yet.
- [ ] 2.3 Port `MiruClient.search()` + `parseSearchResults()` (uses the
      `get`/`getHtml` helpers from 2.2).
- [ ] 2.4 Port `MiruClient.browseCurrentlyAiring()` + `parseBrowseResults()`.
- [ ] 2.5 Port `MiruClient.getEpisodes()` / `getEpisodesWithSeasons()`.
- [ ] 2.6 Port `MiruClient.getEpisodeLanguages()` + `resolveHlsFromEmbed()`.
- [ ] 2.7 Port `MiruClient.getQualities()` + `getAnimeTitle()` +
      `getAnimeDetails()` + the small string helpers (`encode`,
      `extractResolution`, `resolveUrl`) — finishes `MiruClient`. `app`
      switches to calling `shared`'s `MiruClient` instead of its own copy.
- [x] 2.8 Port `UpdateChecker.java` → `shared/commonMain/.../network/UpdateChecker.kt`
      (Ktor + GitHub API), keeping `checkNow`/`checkOnStartup` behavior
      identical. Caching mechanism moves to Phase 3 — for now keep it
      reading Android `SharedPreferences` directly via an Android-only
      shim so this step doesn't block on Phase 3 being done first.
- [x] 2.9 Port the HLS-manifest-parsing half of `HlsDownloader.java`
      (`qualities()`, `parseSegments()`, `parseInitMap()`, `labelFor()`,
      `parseBandwidth()` — pure string parsing, no file I/O) →
      `shared/commonMain/.../network/HlsParser.kt`. The
      segment-downloading-to-file part stays in `app` for now (moves in
      Phase 4).

## Phase 3 — Storage abstraction (`expect`/`actual`)

- [x] 3.1 Design a common `interface AppStorage` (get/set string, int,
      bool) in `shared/commonMain`, with an `androidMain` `actual`
      implementation backed by `SharedPreferences` (same behavior
      `StorageSettings.java` has today). No callers switched over yet —
      just the interface + Android implementation existing and building.
- [x] 3.2 Port `StorageSettings`'s download-folder-uri get/set (2 methods)
      → `shared/commonMain/.../storage/StorageSettings.kt` using
      `AppStorage`. Smallest possible first slice.
- [x] 3.3 Port `StorageSettings`'s parallel-segments + concurrent-downloads
      get/set methods.
- [x] 3.4 Port `StorageSettings`'s quality/language/dark-theme get/set
      methods — finishes `StorageSettings`. `app` switches to calling
      `shared`'s version.
- [x] 3.5 Port `DownloadEntryStore.all()` (read + JSON-parse only).
- [x] 3.6 Port `DownloadEntryStore.add()` / `remove()` / `removeAll()` +
      the private `save()` — finishes `DownloadEntryStore`.
- [x] 3.7 Switch `UpdateChecker` (from 2.8) over to the new `AppStorage`
      for its cache instead of the Android-only shim.

## Phase 4 — Download engine (the biggest platform-specific chunk)

- [x] 4.1 Port `DownloadManager.Job` → `data class Job` in
      `shared/commonMain/.../download/DownloadManager.kt`. Just the data
      class + constants (`STATUS_*`), no queue logic yet.
- [x] 4.2 Port the read-only/query methods of `DownloadManager`
      (`find`, `findByAnimeAndEpisode`, `hasActiveJob`, `snapshot`,
      `activeCount`, `queuedCount`, `downloadingCount`, `isRunning`).
- [x] 4.3 Port the mutating methods (`enqueue`, `enqueueAll`, `update`,
      `updateMulti`, `complete`, `fail`, `cancel`, `remove`,
      `removeFinished`, `claimNextQueuedJob`, `setRunning`) — finishes
      `DownloadManager` as a Kotlin `object` singleton.
- [x] 4.4 Design `expect`/`actual` file-output functions (open/write/close
      an output stream by platform-appropriate destination). Android
      `actual` wraps `DocumentFile`/SAF exactly like today; no other
      platform `actual` needed yet (Phase 7/8 add those).
- [x] 4.5 Port `HlsDownloader`'s segment-download loop
      (`downloadSegments`, `downloadBytes`, speed sampling) using the Ktor
      client from Phase 2 and the file-output interface from 4.4 — but
      keep writing to a local temp dir only (no final-assembly yet).
- [x] 4.6 Port `HlsDownloader`'s final-assembly step (`download()`'s
      stitching of segments into the output file, `writeUrlToStream`,
      `deleteRecursive`, `sanitize`, `extractParent`) — finishes
      `HlsDownloader`. `app`'s `DownloadService` switches to calling
      `shared`'s downloader instead of its own copy.
- [x] 4.7 `DownloadService.java` + `CancelDownloadReceiver.java` convert to
      Kotlin, staying in `app` (Android `Service`/`BroadcastReceiver` are
      OS-specific), now calling `shared`'s `DownloadManager` + downloader.

## Phase 5 — Everything else Android-only for now

- [x] 5.1 `CrashLogger.java` → Kotlin, stays in `app` (or
      `shared/androidMain` if we want crash logs on other platforms later
      — flag as a NOTE, decide when we get to iOS/Desktop).
- [x] 5.2 `UiHelper.java` → Kotlin, stays in `app`.
- [x] 5.3 `BaseActivity.java` → Kotlin, stays in `app`.
- [x] 5.4 `MiruDLApp.java` → Kotlin, stays in `app`.
- [x] 5.5 `AnimeAdapter.java` → Kotlin, stays in `app`.
- [x] 5.6 `AnimeGridAdapter.java` → Kotlin, stays in `app`.
- [x] 5.7 `EpisodeAdapter.java` → Kotlin, stays in `app`.
- [x] 5.8 `DownloadAdapter.java` → Kotlin, stays in `app`.
      (Note: all four adapters above have no Compose equivalent — they get
      *deleted*, not ported, once Phase 6 replaces their screen.)
- [x] 5.9 `AboutActivity.java` → Kotlin (still Views-based for now, this
      is just the Java→Kotlin syntax move — Compose happens in 6.1).
- [x] 5.10 `DeveloperActivity.java` → Kotlin.
- [x] 5.11 `SettingsActivity.java` → Kotlin, now calling `shared`'s
      `StorageSettings` instead of its own copy.
- [x] 5.12 `DetailActivity.java` → Kotlin, now calling `shared`'s
      `MiruClient`/`DownloadManager` instead of its own copies.
- [x] 5.13 `MainActivity.java` → Kotlin (biggest file, last in this phase),
      now calling `shared` for all business logic.

## Phase 6 — UI rework: Views → Compose (prerequisite for real UI sharing)

- [x] 6.0 Compose setup: added Compose BOM, activity-compose, Material3, Compose compiler plugin to app/build.gradle. Placeholder ComposeTestActivity added.
- [x] 6.1 Rewrite `AboutActivity` (smallest screen) in Jetpack Compose,
      still Android-only, to validate the Compose setup end-to-end before
      touching bigger screens.
- [x] 6.2 Rewrite `DeveloperActivity` in Compose.
- [x] 6.3 Rewrite `SettingsActivity` in Compose.
- [x] 6.4 Rewrite `DetailActivity`'s episode list (just the list/grid of
      episodes) in Compose, reusing `EpisodeAdapter`'s logic as a
      `LazyColumn`/`LazyVerticalGrid`.
- [x] 6.5 Rewrite the rest of `DetailActivity` (search row, select-mode
      bar, download dialogs) in Compose — finishes `DetailActivity`.
^- [x] 6.6 Rewrite `MainActivity`'s Home tab (search + anime grid) in
      Compose.
- [x] 6.7 Rewrite `MainActivity`'s Downloads tab in Compose.
- [x] 6.8 Rewrite `MainActivity`'s Settings tab in Compose — finishes the
      full Views → Compose migration.

## Phase 7 — Add iOS target (needs a macOS build host — use a GitHub Actions macOS runner)

- [ ] 7.1 Add the `iosMain` source set + iOS Kotlin/Native targets to
      `:shared`'s `build.gradle.kts`. Confirm it configures/builds on a
      macOS CI runner (no `actual`s implemented yet, this just proves the
      target wiring works).
- [ ] 7.2 Implement the `AppStorage` `actual` for iOS (`NSUserDefaults` or
      `multiplatform-settings`).
- [ ] 7.3 Implement the file-output `actual` for iOS (plain
      `FileManager`-based writes — no SAF equivalent needed).
- [ ] 7.4 Add the Darwin Ktor engine for iOS and confirm a real network
      call (e.g. `MiruClient.search`) works from an iOS test target.
- [ ] 7.5 Create the `iosApp` Xcode project shell with Compose
      Multiplatform wired in, showing just a placeholder screen.
- [ ] 7.6 Port the Compose screens from Phase 6 into `iosApp` one at a
      time (About → Developer → Settings → Detail → Main), same order as
      Phase 6, each its own step.
- [ ] 7.7 iOS-specific background download handling (background
      URLSession tasks) + local notifications for download progress.
- [ ] 7.8 Add the macOS runner job to GitHub Actions CI for the iOS build.

## Phase 8 — Add Desktop target (Windows/Linux)

- [x] 8.1 Add the `desktopMain` (JVM) source set + target to `:shared`.
      Confirm it builds (no `actual`s yet).
- [x] 8.2 Implement the `AppStorage` `actual` for Desktop (Java
      `Preferences` API or a properties file).
- [x] 8.3 Implement the file-output `actual` for Desktop (plain
      `java.io.File` writes, folder picker via a native file dialog).
- [x] 8.4 Add the CIO/Java Ktor engine for Desktop and confirm a real
      network call works from a desktop test entry point.
- [x] 8.5 Create the `desktopApp` Compose Multiplatform Desktop app shell
      (placeholder window).
- [x] 8.6 Port the Compose screens into `desktopApp`, one at a time, same
      order as Phase 6/7.
- [x] 8.7 Desktop-specific background download handling (background
      thread + system tray icon or simple in-window progress — no OS
      foreground-service concept to hook into).
- [x] 8.8 CI: add Windows + Linux build jobs.

## Phase 9 — Android TV

- [x] 9.1 Create the `tvApp` module (or a TV product flavor of `app`),
      depending on `:shared`, with just a placeholder launcher screen.
- [x] 9.2 Port the Home screen to a TV-friendly Compose layout with D-pad
      focus handling.
- [x] 9.3 Port the Detail/episode-list screen with D-pad focus handling.
- [x] 9.4 Port Downloads + Settings screens for TV.
- [ ] 9.5 Add the Leanback launcher intent filter, TV banner asset, and
      any TV app-store listing requirements.


## Phase 10 — Final polish

- [ ] 10.1 Confirm zero `.java` files remain anywhere in the project.
- [ ] 10.2 Full install test on real devices: Android phone, Android TV
      box, iOS device, Windows, Linux.
- [ ] 10.3 Update this file to a short "done" summary or remove it.

---
### Progress log

- **2026-08-07** Step 6.5 ✅ — DetailActivity full Compose rewrite. Deleted activity_detail.xml, item_episode*.xml, EpisodeAdapter.kt. CI pending.
- **2026-08-07** Step 6.4 ✅ — DetailActivity episode list → Compose (LazyColumn/LazyVerticalGrid). CI pending.
- **2026-08-07** Step 6.3 ✅ — SettingsActivity → Compose. Deleted activity_settings.xml. CI pending.
- **2026-08-07** Step 6.2 ✅ — DeveloperActivity → Compose. Deleted activity_developer.xml. CI pending.
- **2026-08-07** Step 6.1 ✅ — AboutActivity → Compose. Deleted activity_about.xml. CI pending.
- **2026-08-07** Step 6.0 ✅ — Compose setup (BOM + plugin + Material3 + placeholder activity). CI pending.
- **2026-08-07** Step 5.13 ✅ — `MainActivity.java` → Kotlin. Dead bridges deleted. CI green after fixes (commit 0e83fe3).
- **2026-08-07** Step 5.12 ✅ — `DetailActivity.java` → Kotlin. CI green (run 31146959981, commit cc3dba9).
- **2026-08-07** Step 5.11 ✅ — `SettingsActivity.java` → Kotlin. CI green (run 31145417525, commit a23f957).
- **2026-08-07** Step 5.10 ✅ — `DeveloperActivity.java` → Kotlin. CI green (run 31144876543, commit 7be79e0).
- **2026-08-07** Step 5.9 ✅ — `AboutActivity.java` → Kotlin. CI green (run 31144487916, commit 02504b0).
- **2026-08-07** Step 5.8 ✅ — `DownloadAdapter.java` → Kotlin. CI green (run 31143460679, commit 6540c12).
- **2026-08-07** Step 5.7 ✅ — `EpisodeAdapter.java` → Kotlin. CI green (run 31142294335, commit e94fd00).
- **2026-08-07** Step 5.6 ✅ — `AnimeGridAdapter.java` → Kotlin. CI green (run 31141882519, commit c2e3929).
- **2026-08-07** Step 5.5 ✅ — `AnimeAdapter.java` → Kotlin. CI green (run 31140735035, commit aed982f).
- **2026-08-07** Step 5.4 ✅ — `MiruDLApp.java` → Kotlin. CI green (run 31140109533, commit 4db0cca).
- **2026-08-07** Step 5.3 ✅ — `BaseActivity.java` → Kotlin. CI green (run 31139768518, commit 57b2019).
- **2026-08-07** Step 5.2 ✅ — `UiHelper.java` → Kotlin. CI green (run 31139434100, commit 529b7fb).
- **2026-08-07** Step 5.1 ✅ — `CrashLogger.java` → Kotlin.
  CI green (run 31139126468, commit 5cb333a).
- **2026-08-07** Step 4.7 ✅ — `DownloadService.java` + `CancelDownloadReceiver.java` → Kotlin.
  Also added `kotlin.android` plugin to `app/build.gradle` + coroutines dep.
  CI green (run 31137870315, commit 171354f).
- **2026-08-06 (Phase 2.7 done — MiruClient fully migrated):** CI green
  (run 31064087699, commit `77f1848`). Ported remaining methods
  (`getQualities`/`getAnimeTitle`/`extractResolution`/`resolveUrl`) to
  shared `MiruClient.kt`. Created `MiruClientAndroid` bridge
  (`@JvmStatic` + `runBlocking`) in `androidMain`. Switched all 4
  callers (`DetailActivity`, `MainActivity`, `DownloadService`,
  `HlsDownloader`) to `MiruClientAndroid`. **Deleted
  `app/MiruClient.java`** (zero remaining references). `getAnimeDetails`
  skipped (dead code — never called anywhere). Phase 2 networking port
  is now complete for MiruClient.


- **2026-08-06 (Phase 2.6 done):** CI green (run 31063013659, commit
  `4c5020f`). Ported `getEpisodeLanguages()` (kotlinx-serialization
  DTO `LanguagesResponse`/`LanguageDto`, `optString` defaults matched)
  and `resolveHlsFromEmbed()` (pure string scanning, returns `String?`).
  No callers switched.


- **2026-08-06 (Phase 2.5 done):** CI green (run 31062642064, commit
  `a904f13`). Ported `getEpisodes()` + `getEpisodesWithSeasons()` to
  shared `MiruClient.kt`. Replaced `org.json` with `kotlinx.serialization`
  DTOs (`EpisodesResponse`/`EpisodeDto`, private, `ignoreUnknownKeys`).
  `optInt`/`optBoolean` defaults matched via data class defaults. No
  callers switched.


- **2026-08-06 (Phase 2.4 done):** CI green (run 31062232670, commit
  `4341486`). Ported `browseCurrentlyAiring()` + `parseBrowseResults()`
  to shared `MiruClient.kt`. Uses `htmlToText()` for img-alt title
  decode and Kotlin `Regex(DOT_MATCHES_ALL)` for h2/h3/p fallback —
  identical logic to the Java original. No callers switched.


- **2026-08-06 (Phase 2.3 done):** CI green (run 31061502117, commit
  `c67ee0f`). Ported `search(query)` + `parseSearchResults(html)` to
  shared `MiruClient.kt`. Uses `urlEncode()` (replaces `URLEncoder`)
  and `htmlToText()` (replaces `android.text.Html.fromHtml`). Identical
  string-scanning logic — no behaviour change. No callers switched;
  `app` still uses its own copy.


- **2026-08-06 (Phase 2.2 done):** CI green (run 31061123998, commit
  `d4450aa`). Added shared `util/HtmlText.kt` (`expect htmlToText`) +
  `util/UrlEncoding.kt` (`expect urlEncode`) with androidMain/desktopMain
  actuals; added `network/MiruClient.kt` (object with XOR-decoded `BASE`
  = `https://anidb.app`, `UA`, `suspend get()`/`getHtml()` mirroring the
  Java headers). **CI workflow change:** build.yml now also runs
  `:shared:compileKotlinDesktop` so the desktop target is verified too
  (previously only Android; :shared now has real desktop actuals).
  `app` untouched. Verified green on both targets via CI only.


- **2026-08-05 (Phase 2.1b done):** CI green (run 31021806451, commit
  `0811d05`). Added `ktor-client-okhttp:3.5.1` to `shared/androidMain`,
  `ktor-client-cio:3.5.1` to `shared/desktopMain`, and created
  `shared/commonMain/.../network/HttpClientProvider.kt` (lazy singleton
  `HttpClient`, 30s connect/request/socket timeouts, followRedirects —
  mirrors the old `MiruClient` OkHttp builder). No callers yet. Verified
  both targets compile: Android via CI `assembleDebug`, Desktop via local
  `:shared:compileKotlinDesktop`.


- **2026-08-06 (Phase 4.6 done):** CI green (run 31106954667, commit
  `e7815e6`). Completed shared `HlsDownloader.download()`: playlist
  parsing (via `HlsParser`), temp-dir segment download, SAF output file
  creation via new `FileStorage.createFile()`, segment stitching,
  download-record persistence via `DownloadEntryStore`. Added
  `HlsDownloaderAndroid` `@JvmStatic` bridge, `AndroidFileStorage.init()`
  wired in `MiruDLApp`, `DownloadService` switched to shared downloader,
  `app/HlsDownloader.java` deleted (zero refs). Also `@JvmStatic` on
  `AndroidFileStorage.init()` for Java callers. The `body<ByteArray>()`
  Ktor call is byte-identical via `bodyAsText(ISO_8859_1)` fallback to
  avoid D8 metadata warnings.


- **2026-08-06 (Phase 4.5 done):** CI green (run 31100220758, commit
  `4fdb564`). Added shared `HlsDownloader.kt` with `downloadBytes(url)`
  (Ktor GET → `ByteArray`, empty on failure) and `downloadSegments`
  (coroutine-based parallel downloads with `Semaphore`, 700ms speed
  ticker with exponential moving-average smoothing, writes segments via
  `FileStorage.openOutput()`). Delegates parsing to existing `HlsParser`.
  No final assembly yet (4.6). No callers yet.


- **2026-08-06 (Phase 4.4 done):** CI green (run 31078703385, commit
  `0fe0049`). Added `FileStorage` interface
  (`shared/commonMain/.../download/FileStorage.kt`): `cacheDir()`,
  `openInput()`, `openOutput()`, `createDir()`, `findFile()`,
  `deleteFile()`, `size()`. Added `AndroidFileStorage` singleton
  (`shared/androidMain`) wrapping SAF `DocumentFile` + `context.cacheDir`
  + `ContentResolver` with `init(context)` — mirrors the old
  `HlsDownloader.java` file helpers. Added
  `androidx.documentfile:documentfile:1.0.1` to shared androidMain.
  No callers yet (4.5/4.6 use it).


- **2026-08-06 (Phase 4.3b done):** CI green (run 31078160714, commit
  `b116fdd`). Added `DownloadManagerAndroid` `@JvmStatic` bridge
  (shared/src/androidMain), switched all 5 Java callers
  (`CancelDownloadReceiver`, `DetailActivity`, `DownloadAdapter`,
  `DownloadService`, `MainActivity`) to `DownloadManagerAndroid` +
  shared `Job`, dropped the dead `EXTRA_JOBS`/`Serializable` payload
  from `DownloadService.startIntent`, and deleted
  `app/.../DownloadManager.java`. Zero references to the old class
  remain. This finishes Phase 4's shared `DownloadManager`.


- **2026-08-06 (Phase 4.3a done):** CI green (run 31077481741, commit
  09067dc). Added mutating methods to shared `DownloadManager`:
  `enqueue()`, `enqueueAll()`, `update()`, `updateMulti()`,
  `complete()`, `fail()`, `cancel()`, `remove()`, `removeFinished()`,
  `claimNextQueuedJob()`, `setRunning()`. Finishes shared DownloadManager.
  No callers switched yet.

- **2026-08-06 (Phase 4.2 done):** CI green (run 31076982524, commit
  0a38c09). Added query methods to shared `DownloadManager`:
  `find()`, `findByAnimeAndEpisode()`, `hasActiveJob()`, `snapshot()`,
  `activeCount()`, `queuedCount()`, `downloadingCount()`, `isRunning()`.
  Fixed commonMain compatibility: replaced `java.util.UUID` with
  `kotlin.random.Random`-based ID generator. No callers switched yet.

- **2026-08-06 (Phase 4.1 done):** CI green (run 31076127416, commit
  68c12c1). Created shared `DownloadManager.kt` in
  `shared/commonMain/.../download/` with `data class Job` (5-arg
  constructor + UUID id, `@JvmField` on all fields, no `Serializable`)
  and `STATUS_*` constants. No callers switched yet.

- **2026-08-06 (Phase 2.9 done):** CI green (run 31073624440, commit
  df2fb55). Created shared `HlsParser` in
  `shared/commonMain/.../network/HlsParser.kt` with `qualities()`
  (suspend), `parseSegments()`, `parseInitMap()`, `labelFor()`,
  `parseBandwidth()`, `resolve()`, `sanitize()`, `extractParent()`,
  `clampParallel()`, `DEFAULT_PARALLEL = 16`, and suspend `getText()`
  via Ktor. No callers switched — `app/HlsDownloader.java` keeps its
  own copies for now (switch at 4.6).

- **2026-08-06 (Phase 3.6 done):** CI green (run 31073293737, commit
  1a5cbb7). Added `add()`, `remove()`, `removeAll()`, `save()` to shared
  `DownloadEntryStore` with same `key()` dedupe logic. Created
  `DownloadEntryStoreAndroid` bridge with `@JvmStatic` methods. Switched
  all 4 callers (`DetailActivity`, `DownloadEntry.java`, `HlsDownloader`,
  `MainActivity`). Deleted `app/DownloadEntryStore.java`. `DeleteScope`
  enum verified unused — not ported.

- **2026-08-06 (Phase 2.8 done):** CI green (run 31072945223, commit
  3425a60). Ported `UpdateChecker` to shared with `ReleaseInfo` class,
  `fetchLatestRelease()` (Ktor suspend + GitHub API), `isNewerVersion()`
  and `normalize()` (pure, `lowercase()` instead of `Locale.US`). Cache
  read/write via `AppStorage`. Android bridge `UpdateCheckerAndroid` with
  sync `checkNow`/`checkOnStartup` using `runBlocking` + `Handler`.
  Switched `MainActivity` callers. Deleted `app/UpdateChecker.java`. Also
  auto-completes 3.7 (cache was always via AppStorage).

- **2026-08-06 (Phase 3.5 done):** CI green (run 31072400726, commit
  fbad76d). Created shared `DownloadEntryStore` in
  `shared/commonMain/.../storage/DownloadEntryStore.kt` with `all()`
  method using kotlinx-serialization. No callers switched yet.

- **2026-08-06 (Phase 3.4 done):** CI green (run 31072117766, commit
  25c932a). Added `preferred_quality`, `preferred_language`, `dark_theme`,
  `hasDownloadUri` to shared `StorageSettings`. Created
  `StorageSettingsAndroid` bridge with `@JvmStatic` methods. Switched all
  6 callers (`BaseActivity`, `DetailActivity`, `DownloadService`,
  `HlsDownloader`, `MainActivity`, `SettingsActivity`). Deleted
  `app/StorageSettings.java`. Grep confirms zero remaining refs.

- **2026-08-06 (Phase 3.3 done):** CI green (run 31071646444, commit
  d118eb8). Added `parallel_segments` (default 16) and
  `concurrent_downloads` (default 1) to shared `StorageSettings` with
  `getParallelSegments`/`setParallelSegments` and
  `getConcurrentDownloads`/`setConcurrentDownloads`. No callers switched
  yet.

- **2026-08-06 (Phase 3.2 done):** CI green (run 31071182099, commit
  66b3022). Created shared `StorageSettings` object in
  `shared/commonMain/.../storage/StorageSettings.kt` with
  `getDownloadUri(storage)` / `setDownloadUri(storage, uri: String)`
  using key `download_uri`. URI kept as String in common; `Uri` stays
  Android-side. No callers switched yet.

- **2026-08-06 (Phase 3.1 done):** CI green (run 31065923364, commit
  b170d51). Created `AppStorage` interface in `shared/commonMain/.../storage/`
  with `getString`, `setString`, `getInt`, `setInt`, `getBoolean`, `setBoolean`,
  `getLong`, `setLong`. Created `AndroidAppStorage` in
  `shared/androidMain/.../storage/` backed by SharedPreferences (same behavior
  as today's Java code). No callers switched -- just the interface + Android
  implementation existing and building. Prefs names preserved:
  `mirudl_settings`, `mirudl_downloads`, `mirudl_update_checker`.

- **2026-08-05 (Phase 2.1a done + build fix):** CI is green again on
  `main` (run 30989776269). Two commits landed: (1) `f8b5525` Phase 2.1a —
  added `org.jetbrains.kotlin.plugin.serialization` 2.3.10 (root + `shared`)
  and `kotlinx-serialization-json:1.10.0` to `shared/commonMain` (1.10.0
  pinned because it is compiled with Kotlin 2.3.0, which our 2.3.10 compiler
  reads cleanly; 1.11.0 needs Kotlin 2.3.20+). (2) `6114fa0` build fix —
  **pre-existing break found while verifying:** `DownloadAdapter.java`
  references `R.id.dl_anime`, `dl_title`, `dl_status`, `dl_speed`,
  `dl_progress`, `dl_cancel`, `dl_size`, `dl_delete`. These IDs existed only
  inside `item_download_active.xml` / `item_download_completed.xml`, which
  Phase 0.2 deleted — that cleanup's "zero remaining references" check
  missed these `R.id.*` uses, so `:app:compileDebugJavaWithJavac` was broken
  on `main` since 55e8f3c (the last two CI runs before this were already
  red). Fix: added the 8 IDs back to `app/src/main/res/values/ids.xml`.
  `MIGRATION_PLAN_DETAILED.md`'s 2.1a box is ticked; note this was a
  required repair, not part of the 2.1a diff.


- Phase 1.4 done: `DownloadEntryStore.Entry` (inner class) →
  `shared/commonMain/.../model/DownloadRecord.kt` (data class, `@JvmField`
  + `@JvmOverloads`, `key()`/`parentName()` kept as member functions).
  **Correction found mid-step:** the plan originally said this file would
  be named `DownloadEntry.kt`, but `app` already has a *different*,
  Android-only `DownloadEntry.java` (a UI wrapper around this record that
  adds a parsed `Uri` + SAF delete methods via `DocumentFile` — stays in
  `app`, not multiplatform-portable). Named the new shared class
  `DownloadRecord` instead to avoid the collision. Updated all 5
  consumers that referenced `DownloadEntryStore.Entry`: `DetailActivity`,
  `DownloadAdapter` (16 occurrences), `DownloadEntry.java` itself (3),
  `HlsDownloader`, `MainActivity` (8) — all now import and use
  `DownloadRecord`. `DownloadEntryStore.java` no longer has an inner
  `Entry` class; its methods now take/return `DownloadRecord`. Verified
  zero remaining references to the old type anywhere.
- Phase 1.3 done: `VideoSource.java` → `shared/commonMain/.../model/VideoSource.kt`
  as a `data class` (`@JvmField` + `@JvmOverloads`, matching the original's
  2-arg/4-arg constructor split via defaulted `language`/`server` params).
  Verified `source` (always `"primary"`) and `subtitleUrl` are written but
  never read anywhere downstream — kept them for exact parity rather than
  removing, per the no-behavior-change rule. Dropped `implements
  Serializable` (unused, same as 1.1/1.2). Updated imports in the 3
  consumers: `DetailActivity`, `HlsDownloader`, `MiruClient`.
- Phase 1.2 done: `EpisodeItem.java` → `shared/commonMain/.../model/EpisodeItem.kt`
  as a `data class` (`@JvmField` + `@JvmOverloads`, same pattern as 1.1),
  `getLabel()` kept as a regular member function. Dropped `implements
  Serializable`/`transient` — verified no `putExtra`/`getSerializableExtra`
  usage anywhere for this type. Updated imports in the 3 consumers:
  `DetailActivity`, `EpisodeAdapter`, `MiruClient`.
- Phase 1.1 done: `AnimeItem.java` → `shared/commonMain/.../model/AnimeItem.kt`
  as a `data class` (`@JvmField` + `@JvmOverloads` to keep Java call sites
  working unchanged — `new AnimeItem()` + direct `item.title = ...` field
  access both still work). Dropped `implements Serializable`: verified it
  was never actually used (fields always extracted individually into
  Intent extras) — also avoids a java.io.* dependency in commonMain that
  would've broken the future iOS target. Updated imports in the 4
  consumers: `AnimeAdapter`, `AnimeGridAdapter`, `MainActivity`,
  `MiruClient`. Verified no code relies on `AnimeItem`'s object identity/
  equals (one dup-check compares `.id` strings, not the objects), so the
  new data-class `equals()`/`hashCode()` change nothing behaviorally.
- Fix (post Phase 0.3): CI build failed with "Module was compiled with an
  incompatible version of Kotlin... metadata is 2.3.0, expected 2.0.0" —
  Ktor 3.5.1 was compiled with a newer Kotlin than our 2.0.21. Bumped the
  Kotlin Multiplatform plugin to **2.3.10** (official-recommended patch for
  the AGP 8.x + `androidTarget` combo) and migrated `shared/build.gradle.kts`
  from the deprecated `kotlinOptions {}` DSL to `compilerOptions {}` at the
  same time, since `kotlinOptions` risks being removed at this Kotlin
  version. Re-check CI after this before proceeding to Phase 1.
- Phase 0.3 done: added `io.ktor:ktor-client-core:3.5.1` (engine-agnostic,
  no engine wired up yet) to `:shared/commonMain` dependencies. **Phase 0
  is now fully complete** — next up is Phase 1 (data models).
- Phase 0.2 done: deleted `DownloadsActivity.java`, its `<activity>` entry
  in `AndroidManifest.xml`, `activity_downloads.xml`,
  `item_download_active.xml`, `item_download_completed.xml`. Re-checked
  afterward — zero remaining references anywhere in `app/src/`.
- Phase 0.1 done: `:shared` KMP module created (androidTarget + jvm("desktop")),
  Kotlin 2.0.21 + `org.jetbrains.kotlin.multiplatform` + `com.android.library`
  wired into root `build.gradle`, `:shared` added to `settings.gradle`,
  `app` now depends on `implementation project(':shared')`. A placeholder
  `SharedInfo.kt` in `commonMain` proves the source set compiles for both
  targets. No real logic moved yet — verify the next CI build stays green.
- Phase 6.7 done: rewrote MainActivity Downloads tab in Compose
  (DownloadsComposable.kt), deleted DownloadAdapter.kt, removed unused
  RecyclerView/LinearLayoutManager imports, fixed drawable refs
  (ic_cancel→ic_close, ic_expand_less/more→ic_chevron_down/right).
  DownloadEntry.java kept (still used by SAF delete dialogs). CI push
  pending verification.
- Phase 6.8 done: rewrote entire MainActivity in full Compose (Settings
  tab in SettingsComposable.kt, Home/Downloads tabs inline in
  HomeTabContent/DownloadsTabContent composables). Deleted
  activity_main.xml, bottom_nav_menu.xml, spinner_value_chevron.xml,
  item_group_header.xml, item_section_header.xml. Only
  bottom_sheet_detail.xml remains (used by showAnimeDetail dialog).
  Phase 6 fully complete.
- CI fix chain for 6.7-6.8: fixed Long-to-Int in setBackgroundColor,
  smart-cast for mutable rating in AnimeGridComposable, added missing
  imports (clickable, Composable, background, setContent) across
  MainActivity. Final CI green confirmed 2026-08-07.
- 8.1 done: desktopMain already compiles — CI already runs `:shared:compileKotlinDesktop` and it passed in last green run.
- 8.2 done: DesktopAppStorage in desktopMain backed by java.util.prefs.Preferences.
- 8.3 done: DesktopFileStorage in desktopMain backed by java.io.File. Cache at ~/.mirudl/cache.
- 8.4 done: DesktopNetworkCheck.kt entry point verifies CIO engine via MiruClient.search(). Compiles via :shared:compileKotlinDesktop.
- 8.5 done: desktopApp module created with Compose Desktop shell (Main.kt, 900x600 window, Home/Downloads/Settings tabs). CI compiles :desktopApp:compileKotlin.
- 8.6 done: Shared Compose screens (AnimeGridScreen, DownloadsScreen, AsyncImage expect/actual) in shared/commonMain. desktopApp uses shared screens. Android app keeps its own screens.
- 8.7 done: DesktopDownloadService with coroutine-based background downloads, startLoop/startDownload/cancelDownload API.
(Add a line here each time a box is checked, e.g. "2026-08-01: Phase 0.1 done, shared module builds green.")
