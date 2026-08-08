# MiruDL — Detailed Sub-Step Migration Plan

Companion to `MIGRATION_PLAN.md`. Splits every remaining box (Phase 2–10)
into small, buildable, reviewable sub-steps so each one can land in a
single commit and CI (`./gradlew assembleDebug` via GitHub Actions) stays
green after every step. The rule of thumb: **one sub-step = one green
build; `app` must always compile and run.**

Current state (verified 2026-08-07): Phases 0-5 fully done. `app` has 1 remaining Java file:
Phase 6 fully complete. 1 XML layout remains (bottom_sheet_detail.xml for showAnimeDetail dialog).

---

## New technical constraints found during analysis (read before starting)

These were NOT explicit in the original plan and will bite if ignored:

1. **`org.json` does not exist in commonMain.** `MiruClient`,
   `DownloadEntryStore`, and `UpdateChecker` all parse JSON with
   `org.json.JSONObject/JSONArray`, which is Android/JVM-only. `shared`
   needs `kotlinx-serialization-json` (plugin + runtime) or a hand-rolled
   parser. Use kotlinx-serialization (step 2.1a). Keep behavior parity:
   `optString` defaults to `""`, `optInt` to `0`, `optBoolean` to
   `false` — mirror those in the DTO defaults.
2. **`android.text.Html.fromHtml` is used in `MiruClient`** title parsing
   (strips tags + decodes entities). commonMain cannot touch it →
   `expect fun htmlToText()` with Android actual (`Html.fromHtml`) and a
   Desktop actual (regex strip + entity decode, revisit in Phase 8).
3. **`java.net.URLEncoder` is JVM-only.** `MiruClient.encode()` uses it →
   `expect fun urlEncode()`; both Android and Desktop actuals use
   `URLEncoder.encode(value, "UTF-8")` (keeps `+` behavior, exact parity).
4. **Java cannot call `suspend` functions.** Until `app` is fully Kotlin
   (end of Phase 5), every shared Ktor/suspend API needs a synchronous
   `@JvmStatic` bridge in `androidMain` using `runBlocking`. Today all
   these calls already run on background threads, so `runBlocking` is
   behavior-identical. Delete each bridge when its last Java caller is
   gone.
5. **`DownloadManager.Job implements Serializable` is used to stuff jobs
   into the service Intent — but the payload is DEAD CODE.** Verified:
   `DownloadService.onStartCommand` never reads `EXTRA_JOBS`; it only
   reads the in-memory `DownloadManager`. shared `Job` must NOT implement
   `java.io.Serializable` (breaks iOS). So at 4.3, drop the payload and
   change `DownloadService.startIntent(Context, List<Job>)` →
   `startIntent(Context)` (or accept job IDs) in the same commit.
6. **Storage keys must stay identical** so users don't lose data:
   prefs files `mirudl_settings`, `mirudl_downloads`,
   `mirudl_update_checker` and their keys move verbatim into the
   `AppStorage` Android implementation.
7. **`HlsDownloader.DEFAULT_PARALLEL` is referenced by
   `StorageSettings`.** Move the constant into shared early (step 2.9)
   so Phase 3 and 4 can both use one source of truth.
8. **Compose setup step is missing from the original plan.** Phase 6
   needs a new step 6.0 (add Compose BOM + `activity-compose` +
   Material3 to `app/build.gradle`).
9. **`jvm("desktop")` + `desktopMain` already exist** (Phase 0.1) →
   Phase 8.1 is effectively already done; just verify it still builds.
10. **`release.keystore` does not exist** in the repo. Release signing
    config references it, so only `assembleDebug` (what CI runs) works
    today. Not a migration blocker — noted for Phase 10.2.

---

## Dependency map (who uses whom) — verified by grep

```
MiruClient        ← DetailActivity, DownloadService, HlsDownloader, MainActivity
HlsDownloader     ← DownloadService, StorageSettings (DEFAULT_PARALLEL)
DownloadManager   ← CancelDownloadReceiver, DetailActivity, DownloadAdapter,
                    DownloadService, MainActivity
StorageSettings   ← BaseActivity, DetailActivity, DownloadService, HlsDownloader,
                    MainActivity, SettingsActivity
DownloadEntryStore← DetailActivity, DownloadEntry, HlsDownloader, MainActivity
DownloadEntry     ← MainActivity (Android-only SAF wrapper, stays in app until Phase 6)
UpdateChecker     ← MainActivity
CrashLogger       ← MainActivity, MiruDLApp
UiHelper          ← MainActivity
BaseActivity      ← About, Detail, Developer, Main, Settings activities
```

Ordering principle: move **leaf** logic first (no deps), then the things
that depend on it, then switch `app` callers over, then delete the
original. Every step keeps both copies temporarily where needed — that's
the strangler-fig pattern working as intended.

---

## Phase 2 — Networking → `shared/commonMain` (with reorder note)

> **Reorder vs. original:** do 3.1 (AppStorage) before 2.8 so
> `UpdateChecker` can use it directly instead of a throwaway Android
> shim. Everything else keeps the original sequence.

### 2.1a Build: add kotlinx-serialization to `:shared`
- Files: root `build.gradle`, `shared/build.gradle.kts`
- Add `org.jetbrains.kotlin.plugin.serialization` version `2.3.10`
  (apply false at root), apply in `shared`, add
  `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")`
  to `commonMain` (verify the latest compatible version at build time).
- No behavior change; nothing else touched.
- Verify: `./gradlew :shared:compileDebugKotlinAndroid assembleDebug`

### 2.1b Build: Ktor engines + HttpClientProvider
- Files: `shared/build.gradle.kts`,
  `shared/src/commonMain/kotlin/msr/mirudl/shared/network/HttpClientProvider.kt`
- Add `implementation("io.ktor:ktor-client-okhttp:3.5.1")` to
  `androidMain`, `implementation("io.ktor:ktor-client-cio:3.5.1")` to
  `desktopMain`. `HttpClientProvider` returns one configured
  `HttpClient` (30s timeouts, follow redirects — mirrors
  `MiruClient`'s OkHttp builder). No callers yet.
- Verify: `./gradlew assembleDebug`

### 2.2 common utilities + shared MiruClient HTTP layer
- New `shared/src/commonMain/.../util/HtmlText.kt` (`expect fun htmlToText`)
  + `util/UrlEncoding.kt` (`expect fun urlEncode`).
- New `shared/src/commonMain/.../network/MiruClient.kt`: `object`
  with `BASE` (port the XOR-decode byte array verbatim), `UA`, and
  `suspend fun get(url)`, `suspend fun getHtml(url)` using Ktor with the
  exact same headers as today.
- New `shared/src/androidMain/.../util/HtmlText.android.kt`,
  `UrlEncoding.android.kt` and
  `shared/src/desktopMain/.../util/HtmlText.desktop.kt`,
  `UrlEncoding.desktop.kt` (actuals).
- `app` untouched. Verify: `./gradlew assembleDebug`

### 2.3 `search()` + `parseSearchResults()` + `encode()`
- Port into shared `MiruClient` as `suspend fun search(query)` +
  private `parseSearchResults(html)`. Use `htmlToText()` for the title
  decode (replaces `android.text.Html.fromHtml`) and `urlEncode()`.
- App still uses its own copy. Verify: `./gradlew assembleDebug`

### 2.4 `browseCurrentlyAiring()` + `parseBrowseResults()`
- Same pattern as 2.3 (`htmlToText` for alt/h2/h3 titles).
- Verify: `./gradlew assembleDebug`

### 2.5 `getEpisodes()` + `getEpisodesWithSeasons()`
- Port; replace `org.json` with a private `@Serializable` DTO
  (`id`, `number`, `number2` default 0, `filler` default false) +
  `Json.decodeFromString`. Keep exception semantics.
- Verify: `./gradlew assembleDebug`

### 2.6 `getEpisodeLanguages()` + `resolveHlsFromEmbed()`
- Same JSON approach for languages (`name`, `embed_url`, `code`).
  `resolveHlsFromEmbed` is pure string scanning — port as-is.
- Verify: `./gradlew assembleDebug`

### 2.7 `getQualities()` + `getAnimeTitle()` + `getAnimeDetails()` + helpers
- Port `extractResolution`, `resolveUrl` (pure string helpers).
- Add `shared/src/androidMain/.../network/MiruClientAndroid.kt`:
  `object` with `@JvmStatic` sync wrappers (`search`, `browse...`,
  `getEpisodes`, `getEpisodesWithSeasons`, `getEpisodeLanguages`,
  `resolveHlsFromEmbed`, `getQualities`, `getAnimeTitle`) using
  `runBlocking`.
- Switch callers: `DetailActivity`, `MainActivity`, `DownloadService`
  (`HlsDownloader` too, it calls `MiruClient.BASE`) from
  `MiruClient.getInstance()` → `MiruClientAndroid.INSTANCE`.
- Delete `app/.../MiruClient.java`. Grep for `MiruClient` — zero refs.
- Verify: `./gradlew assembleDebug`

### 2.8 `UpdateChecker` → shared (do AFTER 3.1) ✅
- Files: `shared/src/commonMain/.../network/UpdateChecker.kt`,
  `shared/src/androidMain/.../network/UpdateCheckerAndroid.kt`
- Port `ReleaseInfo` (data class), `fetchLatestRelease()` (Ktor suspend,
  GitHub API, parse assets for `.apk`), `isNewerVersion()` +
  `normalize()` (pure, `lowercase()` instead of `Locale.US`), cache
  read/write via `AppStorage` (from 3.1).
- Android bridge: sync `checkNow`/`checkOnStartup` that run the suspend
  fetch on a background thread and post the callback on the main
  `Handler` (exactly like today's Looper behavior). Bridge stays in
  `androidMain` (it owns `Handler`).
- Switched `MainActivity` call sites → deleted `app/.../UpdateChecker.java`.
- CI green (run 31072945223, commit 3425a60).

### 2.9 `HlsParser` — pure parsing half of `HlsDownloader` ✅
- New `shared/src/commonMain/.../network/HlsParser.kt`: `qualities()`,
  `parseSegments()`, `parseInitMap()`, `labelFor()`, `parseBandwidth()`,
  `resolve()`, `sanitize()`, `extractParent()`, `clampParallel()`,
  `DEFAULT_PARALLEL = 16`, plus a suspend `getText()` via Ktor.
- `app/.../HlsDownloader.java` keeps its own copies for now (switch at
  4.6).
- CI green (run 31073624440, commit df2fb55).

---

## Phase 3 — Storage abstraction

### 3.1 `AppStorage` interface + Android actual ✅
- New `shared/src/commonMain/.../storage/AppStorage.kt`: `interface
  AppStorage { getString(key, default); setString(key, value); getInt;
  setInt; getBoolean; setBoolean; getLong; setLong }`.
- New `shared/src/androidMain/.../storage/AndroidAppStorage.kt`:
  `class AndroidAppStorage(context: Context, prefsName: String)` backed
  by SharedPreferences (same behavior as today). Prefs names preserved:
  `mirudl_settings`, `mirudl_downloads`, `mirudl_update_checker`.
- No callers switched yet. CI green (run 31065923364, commit b170d51).

### 3.2 download-folder-uri get/set ✅
- New `shared/src/commonMain/.../storage/StorageSettings.kt`:
  `object StorageSettings` with `fun getDownloadUri(storage)` /
  `setDownloadUri(storage, uri: String)` using key `download_uri`
  (uri kept as String in common; `Uri` stays Android-side).
- CI green (run 31071182099, commit 66b3022).

### 3.3 parallel-segments + concurrent-downloads ✅
- Port `parallel_segments` (default 16) and `concurrent_downloads`
  (default 1) to shared `StorageSettings`.
- CI green (run 31071646444, commit d118eb8).

### 3.4 quality/language/dark-theme — finishes StorageSettings + switchover ✅
- Port `preferred_quality` (default `"1080p"`), `preferred_language`
  (default `"jpn"`), `dark_theme` (default `true`), `hasDownloadUri`.
- New `shared/src/androidMain/.../storage/StorageSettingsAndroid.kt`:
  `@JvmStatic` bridge taking `Context` (constructs
  `AndroidAppStorage(context, "mirudl_settings")`).
- Switch ALL 6 callers: `BaseActivity`, `DetailActivity`,
  `DownloadService`, `HlsDownloader`, `MainActivity`, `SettingsActivity`.
- Deleted `app/.../StorageSettings.java`. Grep confirms zero remaining
  refs. CI green (run 31072117766, commit 25c932a).

### 3.5 `DownloadEntryStore.all()` read+parse ✅
- New `shared/src/commonMain/.../storage/DownloadEntryStore.kt`:
  `all(storage)` — JSON via kotlinx-serialization
  (`@Serializable` on `DownloadRecord` + a list DTO), same fields,
  same `completedAt` desc sort, same `optString("x","")` defaults.
- CI green (run 31072400726, commit fbad76d).

### 3.6 add/remove/removeAll/save — finishes DownloadEntryStore + switchover ✅
- Port mutations; keep `key()` dedupe logic identical.
- `DeleteScope` enum: verified **unused anywhere** — not ported.
- Android bridge `DownloadEntryStoreAndroid` with `@JvmStatic` methods
  taking `Context` (prefs `mirudl_downloads`, key `entries`).
- Switched callers: `DetailActivity`, `MainActivity`, `DownloadEntry.java`,
  `HlsDownloader.java`. Deleted `app/.../DownloadEntryStore.java`.
- CI green (run 31073293737, commit 1a5cbb7).

### 3.7 UpdateChecker cache via AppStorage
- If 2.8 was done after 3.1 as recommended, this box is already
  satisfied — the cache went through `AppStorage` from the start.
  Mark done and move on.

---

## Phase 4 — Download engine

### 4.1 shared `Job` data class + status constants ✅
- New `shared/src/commonMain/.../download/DownloadManager.kt`:
  `data class Job(...)` with `@JvmField` on all fields, no `Serializable`,
  5-arg constructor + UUID id, `STATUS_*` constants.
- No callers switched. `app`'s `DownloadManager.java` untouched.
- CI green (run 31076127416, commit 68c12c1).

### 4.2 query methods (read-only) ✅
- `find`, `findByAnimeAndEpisode`, `hasActiveJob`, `snapshot`,
  `activeCount`, `queuedCount`, `downloadingCount`, `isRunning`.
- CI green (run 31076982524, commit 0a38c09).

### 4.3a mutating methods (finishes shared DownloadManager) ✅
- `enqueue`, `enqueueAll`, `update`, `updateMulti`, `complete`, `fail`,
  `cancel`, `remove`, `removeFinished`, `claimNextQueuedJob`,
  `setRunning`. Uses `Collections.synchronizedList` + `@Synchronized`.
- CI green (run 31077481741, commit 09067dc).

### 4.3b Java bridge + switchover (delete app copy + dead Intent payload) ✅
- New `shared/src/androidMain/.../download/DownloadManagerAndroid.kt`:
  `@JvmStatic` delegates to shared `DownloadManager` (plain sync calls —
  no `runBlocking` needed because shared uses `@Synchronized`).
- Changed `DownloadService.startIntent(Context, List<Job>)` →
  `startIntent(Context)` (payload was verified dead; `EXTRA_JOBS` +
  `Serializable` payload dropped).
- Switched the 5 callers: `CancelDownloadReceiver`, `DetailActivity`,
  `DownloadAdapter`, `DownloadService`, `MainActivity` →
  `DownloadManagerAndroid` + shared `Job` type.
- Deleted `app/.../DownloadManager.java`. Grep `DownloadManager` — only
  shared imports remain.
- CI green (run 31078160714, commit b116fdd).

### 4.4 file-output abstraction (expect/actual) ✅
- New `shared/src/commonMain/.../download/FileStorage.kt`:
  `interface FileStorage { cacheDir(): String; openInput(path):
  InputStream?; openOutput(path): OutputStream?; createDir(parent,
  name): String?; findFile(parent, name): String?; deleteFile(path):
  Boolean; size(path): Long }` — shaped to what HlsDownloader needs
  (added `openInput` for reading temp segment files in 4.6).
- New `shared/src/androidMain/.../download/AndroidFileStorage.kt`:
  singleton wrapping `DocumentFile`/SAF + `context.cacheDir` +
  ContentResolver, exactly like the old Java helpers. `init(context)`
  must be called once before first use (callers wire it in 4.6/4.7).
- Added `androidx.documentfile:documentfile:1.0.1` to shared
  androidMain (same version as app).
- No callers yet.
- CI green (run 31078703385, commit 0fe0049).

### 4.5 segment-download loop ✅
- Added shared `HlsDownloader.kt` (commonMain) with:
  - `downloadBytes(url)`: Ktor GET returning `ByteArray` (empty on failure)
  - `downloadSegments(urls, storage, tempDir, parallel, progress, cancel)`:
    coroutine-based parallel downloads via `Semaphore`, 700ms speed
    ticker using `CoroutineScope` + `delay()` with exponential moving-average
    smoothing, writes each segment via `FileStorage.openOutput()`
  - `ProgressListener` and `CancelCheck` interfaces
- Delegates to existing `HlsParser` for playlist parsing helpers.
  No final assembly yet (that's 4.6). No callers yet.
- CI green (run 31100220758, commit 4fdb564).

### 4.6 final assembly + switchover ✅
- Completed shared `HlsDownloader.download()`: master playlist check,
  `#EXT-X-KEY` guard, `parseSegments`/`parseInitMap` (via `HlsParser`),
  temp download, cancel check, `createOutputFile` via `FileStorage`,
  stitch segments, `saveDownloadEntry` via shared `DownloadEntryStore`.
- Added `createFile(parent, mimeType, name)` to `FileStorage` interface +
  `AndroidFileStorage` SAF implementation.
- Added `HlsDownloaderAndroid` `@JvmStatic` bridge (runBlocking).
- `AndroidFileStorage.init(context)` wired into `MiruDLApp.onCreate()`
  (method is `@JvmStatic` so Java can call it).
- `DownloadService` switched to `HlsDownloaderAndroid`.
- Deleted `app/.../HlsDownloader.java`. Grep `HlsDownloader` — zero refs.
- `downloadBytes()` uses Ktor `bodyAsText(ISO_8859_1)` → bytes (avoids
  D8 metadata rewriting warnings seen with `body<ByteArray>()`).
- CI green (run 31106954667, commit e7815e6).

### 4.7 DownloadService + CancelDownloadReceiver → Kotlin ✅ (CI green run 31137870315)
- Convert both to Kotlin, staying in `app` (Android Service /
  BroadcastReceiver). Use coroutines + shared `DownloadManager` /
  `HlsDownloader` suspend APIs directly (bridges no longer needed
  here). Remove the now-empty `EXTRA_JOBS` handling completely.
- Keep notification behavior byte-for-byte identical.
- Verify: `./gradlew assembleDebug`

---

## Phase 5 — remaining Java → Kotlin (mechanical, one file per step)

Order matters only for the three activities that touch shared APIs:
do 5.3 (BaseActivity) before 5.9–5.13, and keep 5.11 → 5.12 → 5.13
in that order (each activity is a customer of the previous step's
shared API). Adapt 5.5–5.8 can go in any order; they get **deleted**
in Phase 6, so keep them mechanical and don't gold-plate.

- [x] 5.1 `CrashLogger.java` → Kotlin (stays in app; Android-only: files, ✅ (CI green run 31139126468)
  `StatFs`, `SharedPreferences`).
- [x] 5.2 `UiHelper.java` → Kotlin (trivial). ✅ (CI green run 31139434100)
- [x] 5.3 `BaseActivity.java` → Kotlin (calls shared `StorageSettings`). ✅ (CI green run 31139768518)
- [x] 5.4 `MiruDLApp.java` → Kotlin (Application + notification channel). ✅ (CI green run 31140109533)
- [x] 5.5 `AnimeAdapter.java` → Kotlin. ✅ (CI green run 31140735035)
- [x] 5.6 `AnimeGridAdapter.java` → Kotlin. ✅ (CI green run 31141882519)
- [x] 5.7 `EpisodeAdapter.java` → Kotlin. ✅ (CI green run 31142294335)
- [x] 5.8 `DownloadAdapter.java` → Kotlin. ✅ (CI green run 31143460679) (uses shared `DownloadRecord` +
  shared `Job`).
- [x] 5.9 `AboutActivity.java` → Kotlin. ✅ (CI green run 31144487916)
- [x] 5.10 `DeveloperActivity.java` → Kotlin. ✅ (CI green run 31144876543)
- [x] 5.11 `SettingsActivity.java` → Kotlin (uses shared `StorageSettings`). ✅ (CI green run 31145417525)
- [x] 5.12 `DetailActivity.java` → Kotlin (603 lines, complex UI). ✅
  - **5.12a** Convert: Java → Kotlin, preserving all behavior.
    - `ExecutorService` + `Handler` → `lifecycleScope.launch` + `withContext(Dispatchers.IO)`.
    - `DownloadManagerAndroid.xxx()` → shared `DownloadManager.xxx()` (suspend).
    - `MiruClientAndroid.xxx()` → shared `MiruClient.xxx()` (suspend).
    - `DownloadEntryStoreAndroid.xxx()` → shared `DownloadEntryStore.xxx()`.
    - `StorageSettingsAndroid.xxx()` → shared `StorageSettings.xxx()`.
    - Programmatic dialogs stay as `AlertDialog` builders (Kotlin syntax).
    - `EpisodeAdapter` already Kotlin → no changes.
    - Intent extras: read via `getStringExtra()` / `getIntExtra()`.
  - **5.12b** Delete bridges whose last caller was DetailActivity (grep first).
  - **5.12c** Verify: `./gradlew assembleDebug`, push, CI green.

- [x] 5.13 `MainActivity.java` → Kotlin (1433 lines, biggest file).
  - **5.13a** Convert: Java → Kotlin, preserving all behavior.
    - All shared API calls → direct suspend (coroutines).
    - `UpdateChecker` → shared `UpdateChecker` suspend.
    - `DownloadEntry.java` → keep as-is (stays until 6.7).
    - RecyclerView adapters → keep as-is (already Kotlin).
    - Bottom navigation + ViewPager → keep as-is.
    - XML layout references → keep as-is (Phase 6 rewrites).
  - **5.13b** Delete ALL remaining Android bridges (no Java callers left):
    - `MiruClientAndroid.kt`, `DownloadEntryStoreAndroid.kt`,
    - `DownloadManagerAndroid.kt`, `StorageSettingsAndroid.kt`,
    - `HlsDownloaderAndroid.kt` (dead code).
    - Grep each before deleting — zero refs required.
  - **5.13c** Verify: grep `.java` in `app/src` — only `DownloadEntry.java` remains.
    Push, CI green.

- After 5.13: Phase 5 complete.
- Verify after each: `./gradlew assembleDebug`

---

## Phase 6 — Views → Compose (prerequisite: Phase 5 done)

- **6.0 (new) Compose setup:** add Compose BOM + `activity-compose` +
  Material3 (+ Compose compiler extension matching Kotlin 2.3.10) to
  `app/build.gradle`. Add a tiny placeholder Compose screen reachable
  from a debug menu to prove tooling works. Verify: `assembleDebug`.
- 6.1 Rewrite `AboutActivity` in Compose (smallest). Delete
  `activity_about.xml` + old class.
- 6.2 Rewrite `DeveloperActivity` in Compose.
- 6.3 Rewrite `SettingsActivity` in Compose.
- 6.4 Rewrite `DetailActivity` episode list (grid/list) in Compose
  (`LazyColumn`/`LazyVerticalGrid`), reusing adapter logic.
- 6.5 Rewrite rest of `DetailActivity` (search row, select-mode bar,
  download dialogs) — delete `item_episode*.xml`,
  `EpisodeAdapter.kt`, `activity_detail.xml`.
- 6.6 Rewrite `MainActivity` Home tab (search + anime grid) — delete
  `item_anime*.xml`, `AnimeAdapter.kt`, `AnimeGridAdapter.kt`.
- 6.7 Rewrite `MainActivity` Downloads tab — delete `DownloadAdapter.kt`,
  `DownloadEntry.kt` (its SAF delete logic moves into the Compose
  screen or shared `FileStorage`).
- 6.8 Rewrite `MainActivity` Settings tab — delete
  `activity_main.xml`, `bottom_nav_menu.xml`, remaining Views bits.
- After 6.8, `app` has no XML layouts left except launcher/mipmaps.
- Verify after each: `./gradlew assembleDebug`

---

## Phase 7 — iOS (needs a macOS runner in CI; no code until then)

- 7.1 Add `iosMain` + iOS targets to `shared/build.gradle.kts`. Needs
  the macOS CI job first — add that as 7.0 so 7.1 can be verified.
- 7.2 `AppStorage` actual for iOS (`NSUserDefaults` /
  multiplatform-settings).
- 7.3 File-output actual for iOS (`FileManager`).
- 7.4 Darwin Ktor engine + real network test.
- 7.5 `iosApp` Xcode shell + placeholder screen.
- 7.6 Port Compose screens one at a time (About → Developer → Settings
  → Detail → Main).
- 7.7 iOS background downloads + local notifications.
- 7.8 macOS CI job for iOS build.

---

## Phase 8 — Desktop (Windows/Linux)

- 8.1 **Already done** (Phase 0.1 created `jvm("desktop")` +
  `desktopMain`). Verify: `./gradlew :shared:compileKotlinDesktop` —
  if green, mark complete and move on.
- 8.2 `AppStorage` actual for Desktop (Java `Preferences` or properties
  file).
- 8.3 File-output actual for Desktop (`java.io.File` + folder picker).
- 8.4 CIO/Java engine already added in 2.1b — verify a real network
  call from a desktop test entry point.
- 8.5 `desktopApp` Compose shell.
- 8.6 Port Compose screens one at a time.
- 8.7 Desktop background download handling (thread + tray/in-window
  progress).
- 8.8 CI: Windows + Linux build jobs.

---

## Phase 9 — Android TV

- 9.1 `tvApp` module (or product flavor) depending on `:shared`,
  placeholder launcher.
- 9.2 Home screen with D-pad focus.
- 9.3 Detail/episode list with D-pad focus.
- 9.4 Downloads + Settings screens for TV.
- 9.5 Leanback intent filter, banner asset, TV store listing items.

---

## Phase 10 — Final polish

- 10.1 Confirm zero `.java` files project-wide.
- 10.2 Real-device tests: Android phone, TV box, iOS, Windows, Linux.
  (Note: `release.keystore` missing — generate/commit before testing
  release builds.)
- 10.3 Replace this file + `MIGRATION_PLAN.md` with a short done
  summary.

---

## Recommended execution order (dependency-sorted) — LOCKED

> Follow EXACTLY this order. One sub-step at a time, user-driven.
> Tick each box here when done (also check the box in the phase section
> and add a progress-log line at the bottom of MIGRATION_PLAN.md).

### Phase 2 — Networking
- [x] 2.1a kotlinx-serialization setup
- [x] 2.1b Ktor engines + HttpClientProvider
- [x] 2.2 common utils + shared MiruClient HTTP layer
- [x] 2.3 search() + parseSearchResults() + encode()
- [x] 2.4 browseCurrentlyAiring() + parseBrowseResults()
- [x] 2.5 getEpisodes() + getEpisodesWithSeasons()
- [x] 2.6 getEpisodeLanguages() + resolveHlsFromEmbed()
- [x] 2.7 getQualities()/getAnimeTitle()/helpers + switchover

### Phase 3 — Storage
- [x] 3.1 AppStorage interface + Android actual
- [x] 2.8 UpdateChecker (after 3.1)
- [x] 3.2 download-folder-uri get/set
- [x] 3.3 parallel-segments + concurrent-downloads
- [x] 3.4 quality/language/theme + switchover
- [x] 3.5 DownloadEntryStore.all()
- [x] 3.6 add/remove/removeAll/save + switchover
- [x] 3.7 UpdateChecker cache via AppStorage (= done with 2.8)

### Phase 4 — Download engine
- [x] 4.1 shared Job + STATUS constants
- [x] 4.2 query methods
- [x] 4.3a mutating methods
- [x] 4.3b Java bridge + switchover (delete app copy)
- [x] 4.4 FileStorage expect/actual
- [x] 4.5 segment-download loop
- [x] 4.6 final assembly + switchover
- [x] 4.7 DownloadService + CancelDownloadReceiver → Kotlin

### Phase 5 — Java → Kotlin (13 files)
- [x] 5.1 CrashLogger.java → Kotlin
- [x] 5.2 UiHelper.java → Kotlin
- [x] 5.3 BaseActivity.java → Kotlin
- [x] 5.4 MiruDLApp.java → Kotlin
- [x] 5.5 AnimeAdapter.java → Kotlin
- [x] 5.6 AnimeGridAdapter.java → Kotlin
- [x] 5.7 EpisodeAdapter.java → Kotlin
- [x] 5.8 DownloadAdapter.java → Kotlin
- [x] 5.9 AboutActivity.java → Kotlin
- [x] 5.10 DeveloperActivity.java → Kotlin
- [x] 5.11 SettingsActivity.java → Kotlin
- [x] 5.12 DetailActivity.java → Kotlin (sub: 5.12a convert, 5.12b bridge cleanup, 5.12c verify)
- [x] 5.13 MainActivity.java → Kotlin (sub: 5.13a convert, 5.13b delete all bridges, 5.13c verify)

### Phase 6 — Views → Compose
- [x] 6.0 Compose setup (BOM + activity-compose + Material3 in build.gradle)
- [x] 6.1 AboutActivity → Compose (smallest, proof-of-concept)
- [x] 6.2 DeveloperActivity → Compose
- [x] 6.3 SettingsActivity → Compose
- [x] 6.4 DetailActivity episode list → Compose (LazyColumn/LazyVerticalGrid)
- [x] 6.5 DetailActivity rest → Compose + delete item_episode*.xml + EpisodeAdapter.kt + activity_detail.xml
- [x] 6.6 MainActivity Home tab → Compose + delete item_anime*.xml + AnimeAdapter.kt + AnimeGridAdapter.kt
- [x] 6.7 MainActivity Downloads tab → Compose + delete DownloadAdapter.kt + DownloadEntry.java
- [x] 6.8 MainActivity Settings tab → Compose + delete activity_main.xml + bottom_nav_menu.xml

### Phase 7 — iOS
- [ ] 7.0 macOS CI job setup (GitHub Actions)
- [ ] 7.1 iosMain + iOS targets in shared/build.gradle.kts
- [ ] 7.2 AppStorage iOS actual (NSUserDefaults/multiplatform-settings)
- [ ] 7.3 FileStorage iOS actual (FileManager)
- [ ] 7.4 Darwin Ktor engine + network verification
- [ ] 7.5 iosApp Xcode shell + placeholder screen
- [ ] 7.6 Port Compose screens (About → Developer → Settings → Detail → Main)
- [ ] 7.7 iOS background downloads + local notifications
- [ ] 7.8 macOS CI job verification

### Phase 8 — Desktop (Windows/Linux)
- [x] 8.1 Verify desktopMain compiles (already exists)
- [ ] 8.2 AppStorage Desktop actual (Java Preferences)
- [ ] 8.3 FileStorage Desktop actual (java.io.File + folder picker)
- [ ] 8.4 Verify CIO engine real network call
- [ ] 8.5 desktopApp Compose shell
- [ ] 8.6 Port Compose screens
- [ ] 8.7 Desktop background download handling
- [ ] 8.8 CI: Windows + Linux build jobs

### Phase 9 — Android TV
- [ ] 9.1 tvApp module + placeholder launcher
- [ ] 9.2 Home screen with D-pad focus
- [ ] 9.3 Detail/episode list with D-pad focus
- [ ] 9.4 Downloads + Settings screens for TV
- [ ] 9.5 Leanback intent filter + banner asset

### Phase 10 — Final polish
- [ ] 10.1 Confirm zero .java files project-wide
- [ ] 10.2 Real-device tests: Android, TV, iOS, Windows, Linux
- [ ] 10.3 Replace MD files with short done summary

## Verification checklist (every sub-step)

1. `./gradlew assembleDebug` (same as CI) — must be green.
2. Grep the deleted class name across `app/src` — zero references.
3. If a prefs key or file name changed, diff it against the old class —
   keys/values must be byte-identical (user data compatibility).
4. Push to `main`; confirm the GitHub Actions build + APK upload
   succeeds before starting the next sub-step.
