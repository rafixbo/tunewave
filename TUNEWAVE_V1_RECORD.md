# TuneWave v1 — Build Record

**Date:** 2026-11-26 ~02:30 Dhaka (2026-11-25 ~20:30 UTC), updated ~02:39 Dhaka
**App:** TuneWave 1.0.0+1 · `com.rafixbo.tunewave` · versionCode 1
**Project:** `~/workspace/tunewave/` · local git commit `204c9a4` (branch `main`)
**Source zip:** `~/workspace/your_files/tunewave-v1-source.zip` — 53,668 B, SHA-256 `624c6de77e618b941232ef83576aaaab71d5df9e5c03eac1fcb0c2f370699008` (rebuilt from HEAD via `git archive`)

## Status: CODE COMPLETE + PUSH-READY — APK NOT BUILT (repo creation blocked, see below)

The full v1 app is written, committed locally, and statically verified as far as
this VM allows. **No APK exists yet.** The connector-push route is fully
prepared (34 text files, manifest ready), but the empty GitHub repo could not
be created by the connector — GitHub rejects repo creation by the App
integration. Exact blocker + resume step below.

## Blocker 1 — GitHub Actions (primary build route): gh NOT authenticated
- `gh auth status` → "You are not logged into any GitHub hosts."
- Consequence: no `tunewave` repo could be created, nothing pushed, no Actions
  run triggered. **CI run URL: none.**
- No PAT was hunted or used: the task said to report the auth blocker, and
  credentials must not be recovered from chat/DB without explicit authorisation.

## 2026-11-26 ~02:38 — Connector push attempt (parent task: do it via GitHub connector, no PC push)
- GitHub connector verified connected as **rafixbo** (Nohin Tafsir Rafi, id
  87462837, default account 09f31c27360c4f679ebfcdb6704b8ab3); `search_repositories`
  confirmed **no repo named `tunewave` exists** under rafixbo.
- **Binary fix (commit `204c9a4`):** the 11 binary files (10 PNG launcher
  icons + `gradle/wrapper/gradle-wrapper.jar`) were removed from git; PNGs
  replaced by text vector drawables `res/drawable/ic_launcher.xml` +
  `ic_launcher_round.xml` (original violet wave mark, white/cyan/pink bars);
  `AndroidManifest.xml` now uses `@drawable/ic_launcher` /
  `@drawable/ic_launcher_round` (grep: zero `@mipmap` refs remain); workflow
  no longer uses `./gradlew` — it uses `gradle/actions/setup-gradle@v3`
  (gradle-version 8.12) + `gradle :app:assembleRelease --stacktrace`. XML
  well-formed, workflow YAML parses, `git ls-files` contains **zero binaries**.
- **Snapshot ready:** manifest `/tmp/tunewave-paths.txt` — **34 text files,
  153,289 bytes** (under the 16 MiB connector limit), no sync-record entries.
  The push command is staged and waiting:
  `/opt/hatch/bin/github push-files --owner rafixbo --repo tunewave --branch main --message "TuneWave v1.0.0+1 — Kotlin YouTube Music client (no login, no ads)" --source-dir ~/workspace/tunewave --paths-file /tmp/tunewave-paths.txt`
- **BLOCKER — repo creation rejected by GitHub, twice (default + explicit
  account):** `call-tool create_repository` → `failed to create repository:
  POST https://api.github.com/user/repos: 403 Resource not accessible by
  integration []`. The Muse GitHub App integration token cannot create user
  repositories — `POST /user/repos` requires a user OAuth token, which the
  connector does not expose. This is a hard connector/API limitation, not an
  approval failure (both writes were approved) and not transient.
- **Exact resume step (one tap, no code/git/build on Rafi's side):** create the
  EMPTY public repo `tunewave` under rafixbo on github.com (no README, no
  .gitignore, no license — autoInit must stay off, the snapshot supplies the
  files). Then re-run the staged `push-files` command above (it will ask for
  approval once), confirm the returned commit SHA, record it in
  `~/workspace/tunewave.muse-github-snapshot-sync.json`, and the push to `main`
  triggers `.github/workflows/android.yml` automatically. Then: poll CI to
  green (≤3 fix retries), download artifact `tunewave-v1-apk`, verify with
  apksigner + `aapt2 dump badging` + dex ad-SDK grep, copy APK to
  `~/workspace/your_files/tunewave-v1.apk`.
- NOT attempted: `gh auth login`, PAT hunting in chat/DB, creating the repo
  under the other linked account (rakiofislamist-cell — wrong owner).

## Blocker 2 — Local Gradle build: sandbox loopback interception (PROVEN)
- `./gradlew :app:assembleRelease` (and `--no-daemon`) fail with:
  `DaemonInitialConnectException: The first result from the daemon was empty.`
- Root cause proven with a Java socket test (2026-11-26): a Java client
  "connects" to a Java server on 127.0.0.1, but the server's `accept()` never
  fires — the sandbox intercepts Java TCP to loopback. Python loopback works;
  Java loopback does not. Gradle's launcher↔daemon handshake is Java TCP to
  loopback, so **no Gradle build can run in this VM** (same known limit as the
  Shikho Flutter builds — see `premium-learning-platform/.github/workflows/build-apk.yml` header).
- No docker/podman/kotlinc on this VM to work around it. The Android SDK 36
  was installed manually for a future attempt (see below).

## What was verified (all on this VM, 2026-11-26)
- **Versions checked against live sources (not guessed):**
  - NewPipe Extractor **0.27.1** (latest stable, 2026-11-10) via JitPack
    `com.github.TeamNewPipe:NewPipeExtractor:0.27.1` — API verified against the
    upstream `dev` source (StreamingService/SearchExtractor/StreamExtractor/
    AudioStream/KioskList/NewPipe.init signatures read from GitHub raw files).
  - Media3 **1.8.1** stable; Coil **3.4.0** (proven in After Class Kotlin v7);
    Compose BOM **2025.05.01** + Material 3 **1.3.2** (Expressive stable);
    OkHttp 4.12.0, coroutines 1.8.1, serialization-json 1.7.3, DataStore 1.1.1
    (all proven in the v7 green build); AGP 8.7.3 + Kotlin 2.2.21 + Gradle 8.12
    (proven v7 toolchain, stdlib pinned to 2.2.21 — the 2.2.21 compiler cannot
    read Kotlin 2.3.x metadata).
  - Material 3 Expressive ships in material3 ≥ 1.3.0; 1.3.2 used.
- **Kotlin compile check (direct kotlinc 2.2.21, no Gradle):** all 18 `.kt`
  files parsed — **0 syntax errors**. 1,967 diagnostics are all cascading
  "unresolved reference" errors from the missing Android/Compose/Media3/
  NewPipe classpath; the only non-unresolved diagnostics were re-checked one
  by one and are all cascading artefacts of the same missing classpath
  (e.g. `it[KEY]` typed as Any because DataStore is absent). No genuine
  type/argument bug survived that review. This is NOT a full compile — the
  real compile happens on the CI runner.
- **XML well-formed:** AndroidManifest, themes.xml, strings.xml.
- **Workflow YAML parses** (`.github/workflows/android.yml`).
- **Launcher icons:** originally 10 PNGs; replaced 2026-11-26 (commit
  `204c9a4`) by vector drawables `res/drawable/ic_launcher.xml` +
  `ic_launcher_round.xml` (original TuneWave wave mark, generated, not stock,
  not YouTube/ACS) so the connector text-only push can carry them.
- **No ads:** grep over all sources finds no ad SDK — no AdMob, no ad network,
  no ad dependency (only a code comment stating "ships NO ad SDK").
- **No secrets:** grep finds no API key / password / token / secret (only
  MediaSession/SessionToken false positives). No `.env`, no keystore.
- **SDK installed locally for future attempts:** `platforms;android-36`
  (platform-36_r02.zip, 65,878,410 B) + `build-tools;36.0.0`
  (build-tools_r36_linux.zip) — sdkmanager itself cannot fetch manifests in
  this VM (Java networking), so both were downloaded via curl and installed
  by hand; `local.properties` (gitignored) points at `~/Android/Sdk`.

## v1 features in the code (all present, NOT phone-tested)
- Home: Trending Music (YouTube default kiosk + search fallback), New Releases,
  Top Charts, mood/genre chips → search.
- Search: debounced (400 ms), history (20), filters All/Songs/Playlists/Artists.
- Player: full-screen expressive player, blurred glass art, seek, shuffle/
  repeat, queue, sleep timer (10–60 min), speed 0.5–2×, like, lyrics (LRCLIB),
  download button.
- Background play: Media3 `MediaSessionService` (notification + lockscreen
  controls, screen-off playback). `PlayerManager` singleton + MediaController.
- Library: Liked, History (100), local Playlists (create/add/delete),
  Downloads (offline play from app files, delete).
- Downloads: per-song + per-playlist (`downloadAll`), progress, quality
  setting; stored in app-private `files/downloads/`.
- Settings: stream quality, download quality, theme (Dark/System), sleep
  timer note, clear history/search-history.
- **Developer Details (Settings → Developer):** Rafi / @rafixb, About,
  GitHub `github.com/rafixbo` + Telegram `@rafixb` tappable rows only — no
  other links invented. Initials "R" avatar in app accent (no photo asset
  exists; no photo downloaded, no random person's photo). Hardcoded defaults
  (no admin backend in TuneWave), expressive layout matching the app.
- No login screen anywhere. No onboarding account wall.

## Honest quality limit (also shown in-app)
YouTube's best audio is Opus itag 251 (~160–256 kbps). High = 251, Medium =
AAC 140 (128 kbps), Low = 249 (~70 kbps). **No FLAC / lossless** — TuneWave
never claims it.

## Deviations from the brief (deliberate, recorded)
- **Room deferred to v2:** library/playlists/history/downloads persist as
  JSON via DataStore Preferences + app files instead of Room, to keep v1
  green without a KSP plugin. User-visible behaviour is identical.
- **Unsigned release APK from CI:** no keystore committed (ever). Sign
  outside Gradle if/when Rafi wants a release key.
- **Gradients kept in TuneWave:** the header/player art use violet→pink
  gradients per Rafi's "smooth, shiny" order for *this* app. Note: Rafi
  separately recorded a gradient hate for the *After Class* Kotlin UI
  (2026-11-26) — if he wants TuneWave flat too, it is a one-file theme change.

## Version identity (release-contract check, adapted — no backend/version guard)
- Single source of truth: `app/build.gradle.kts`
  (`applicationId com.rafixbo.tunewave`, `versionCode 1`,
  `versionName "1.0.0+1"`, `compileSdk/targetSdk 36`, `minSdk 24`).
- `BuildConfig.VERSION_NAME` is read on the Settings screen — it is
  generated from the same build.gradle, so it cannot drift.
- Launcher icon is generated original art committed in `res/mipmap-*` and
  referenced by the manifest — no template icon is copied over it at build
  time (the Shikho v36 bug cannot recur: the workflow has no icon-restore step).
- No backend exists for TuneWave, so there is no version guard / kill switch
  to check against. No `x-app-version` header is sent anywhere.
- Generated-code check: kotlinx-serialization is used (`@Serializable`
  Song/Playlist) with the serialization plugin applied in both build files;
  the CI build must be re-inspected for `$$serializer` symbols in classes.dex
  once an APK exists (could not be done without a build — open item below).

## Open items (need CI green first, then Rafi's phone)
1. Trigger CI (Blocker 1) → download APK → verify with apksigner
   (`verify --print-certs`), `aapt2 dump badging` (package, versionCode 1,
   versionName 1.0.0+1, minSdk 24, targetSdk 36), file size + SHA-256,
   dex grep for ad SDKs (must be absent) + `$$serializer` symbols present.
2. Phone test (Rafi): playback past 5 s, background + lockscreen controls,
   download + offline play, search results, trending load on BD network.
3. NewPipe Extractor 0.27.1 is JitPack-built on first CI run — if JitPack is
   slow/unavailable that day, the build log will show it; fallback is
   unauthenticated Innertube (youtubei v1, ANDROID_MUSIC client).
