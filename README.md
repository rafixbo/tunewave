# TuneWave v1 — YouTube Music alternative (Kotlin)

**App:** TuneWave 1.0.0+1 · package `com.rafixbo.tunewave` · minSdk 24 / targetSdk 36 / compileSdk 36
**Stack:** Kotlin + Jetpack Compose + Material 3 Expressive (Android 16 style), Media3 ExoPlayer,
NewPipe Extractor 0.27.1, Coil 3.4.0, DataStore Preferences, OkHttp, kotlinx-serialization.

## What it is
- Backend is **YouTube**, online-based. **No login, no Google account, no API key.**
- Background play (MediaSessionService — notification + lockscreen controls, queue).
- Downloads in the highest available YouTube audio quality + offline library.
- **STRICTLY NO ADS** — zero ad SDK (no AdMob, no ad network), no ad tracking, no ad URLs.
  Audio streams from the extractor carry no ads by nature.
- Settings → **Developer Details**: Rafi / @rafixb, About, GitHub + Telegram links
  (hardcoded defaults — no admin backend in TuneWave; initials "R" avatar).

## Honest quality limit
YouTube's best audio is Opus itag 251 (~160–256 kbps). High = 251, Medium = AAC 140
(128 kbps), Low = 249 (~70 kbps). There is **no FLAC / lossless** on YouTube and
TuneWave never claims otherwise.

## v1 screens
Home (Trending / New Releases / Top Charts / mood chips) · Search (debounced,
history, filters) · Full player (blur/glass art, seek, shuffle/repeat, queue,
sleep timer, speed, like, lyrics via LRCLIB) · Library (Liked / History /
Playlists / Downloads) · Settings (quality, theme, storage) · Developer Details.

## Build (GitHub Actions — primary route)
`.github/workflows/android.yml` builds `:app:assembleRelease` on ubuntu-latest
(JDK 17, Android SDK 36) and uploads the unsigned release APK as artifact
`tunewave-v1-apk`. No secrets needed. No keystore is committed — v1 ships
unsigned from CI; sign outside Gradle if/when Rafi wants a release key.

## Local build
```bash
export PATH=$HOME/jdk17/bin:$PATH
./gradlew :app:assembleRelease
```
Needs Android SDK 36 (`platforms;android-36`, `build-tools;36.0.0`).

## v1 notes / deviations
- **Room deferred to v2:** library/playlists/history/downloads persist as JSON
  via DataStore + app files instead of Room, to keep v1 green without a KSP
  plugin. User-visible behaviour is identical.
- **gh auth blocker (2026-11-26):** `gh auth status` on this VM reports
  "You are not logged into any GitHub hosts", so no `tunewave` repo could be
  created and no Actions run could be triggered from here. To enable CI:
  Rafi (or the main agent, with Rafi's approval) runs `gh auth login` on a
  machine with GitHub access, then pushes this project to a new `tunewave`
  repo — the workflow runs on push to `main`.
- Phone testing still owed: playback past 5s, background + lockscreen,
  download + offline play, search results on a real device.
