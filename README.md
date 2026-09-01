<img src="https://capsule-render.vercel.app/api?type=waving&color=0A0E1A,071825&height=150&section=header&text=VidMax&fontSize=80&fontColor=40E0D0&fontAlignY=50&animation=twinkling&desc=Free%20·%20Open%20Source%20·%20No%20Ads%20·%20No%20Tracking&descAlignY=72&descSize=17&descColor=88cccc" width="100%"/>

<div align="center">

<br/>

<img src="https://skillicons.dev/icons?i=kotlin,androidstudio,github,gradle&theme=dark" height="36" />

<br/><br/>

[![Build](https://github.com/HATAKE2008/vidamx/actions/workflows/build.yml/badge.svg)](https://github.com/HATAKE2008/vidamx/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-MIT-2563EB?style=flat-square&logo=opensourceinitiative&logoColor=white)](./LICENSE)
[![Platform](https://img.shields.io/badge/Android-SDK%2021+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)

[![Material3](https://img.shields.io/badge/Material%203-UI-009688?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Ad-Free](https://img.shields.io/badge/Ads-None%20Ever-DC2626?style=flat-square&logo=adguard&logoColor=white)](#)
[![Themes](https://img.shields.io/badge/Themes-28%20Offline-EA580C?style=flat-square)](#-themes)
[![Streaming](https://img.shields.io/badge/Music-Streaming-FF0044?style=flat-square&logo=youtubemusic&logoColor=white)](#-online-music-streaming)
[![Network](https://img.shields.io/badge/Network-SMB%20FTP%20WebDAV-0EA5E9?style=flat-square)](#-network-streaming-new-in-v114)

<br/>

**Latest Release: [VidMax v1.1.4](https://github.com/HATAKE2008/vidamx/releases/tag/v1.1.4) · [⬇ Download APK](https://github.com/HATAKE2008/vidamx/releases/download/v1.1.4/VidMax-v1.1.4-release.apk) (22.7 MB)**

</div>

---

## ✦ &nbsp;Why VidMax?

> **No ads. No tracking. No bloat.**

A fast, beautiful media player for local video & music — plus a full-featured online music streaming experience built for privacy. Primary engine: **Jetpack Media3 (ExoPlayer)**. Secondary: **MPV** for broader codec support.

---

## 🆕 &nbsp;What's New in v1.1.4

> **Massive update** — Network streaming, rebuilt player & tons of polish. [Full changelog →](https://github.com/HATAKE2008/vidamx/releases/tag/v1.1.4)

### 🌐 Network Streaming (SMB / FTP / WebDAV)
▸ Brand-new **Network tab** to browse and play media directly from your SMB, FTP and WebDAV servers (mpvRex-style proxy streaming)  
▸ Floating bottom bar + video card design for the Network screen  
▸ Robust HTTP source handling so remote files play through a local proxy — SMB/WebDAV/FTP playback repaired  
▸ Re-scan fixes for duplicated recent links when opening the Network tab  
▸ Scrollable "Add Server" dialog on small screens  

### 🔗 Stream Link
▸ **Stream Link** feature — paste an HTTP stream URL and play it  
▸ Added **HLS / DASH / SmoothStreaming** support with a more robust HTTP source  

### ▶ Video Player — Rebuilt
▸ Rebuilt **mpvRex/MPVEx-style player controls** with a redesigned Settings sheet  
▸ **Subtitle & Audio track panel** with ExoPlayer subtitle/audio track support  
▸ Settings sheet redesigned into categories (Controls, Aesthetics, Gestures, Advanced)  
▸ Smooth MX/VLC-style **pinch zoom** with EMA smoothing, dead zone and 8.0 sensitivity  
▸ Gesture handling rework — volume, brightness and zoom gestures now use proven math and never drop touches  
▸ **Volume boost up to 200%** (video + music), shown as a percent-based indicator  
▸ Settings panels in landscape show as a **right-side panel with a live video preview**; in portrait the sheet properly **overlays the player** without squishing the video  
▸ Keep the video fitted and centered across aspect-ratio and orientation changes  

### 📚 Library — Video Playlists & Favorites
▸ mpvRex-style **video playlists & favorites** with a new Library view model  
▸ Glide Compose thumbnails for playlist details, FAB overlap & detail chrome fixes  

### 🏠 Home / Music
▸ Meld-style **online home feed** with animated sliders, mood playlists and favorites section  
▸ **No-login recommendation system** for the home feed with randomized suggestions  
▸ Expandable search bar, song-only filtering, artist discovery  
▸ HQ thumbnails, online favorites, and a new online player look with a stream badge  
▸ **Unified online + offline music player** with ExoPlayer streaming playback  
▸ BloomeeTunes-style animated nav bar (ripple, expanding pill, fade & scale page transitions)  
▸ Video/Folder segmented toggle on the home top bar  

### ⚙ App & CI
▸ **In-app GitHub update checker** — the app auto-detects new releases (Settings → Updates)  
▸ **Font changer** with built-in fonts + TTF/OTF importer  
▸ Splash screen fixes (no black screen on launch, animated vector logo)  
▸ App size cleanup — dead code removed, R8 code + resource shrinking enabled (22.7 MB)  
▸ Release workflow auto-builds, signs and publishes a GitHub release from a version tag

---

## ♫ &nbsp;Online Music Streaming &nbsp;[![](https://img.shields.io/badge/-NEW-FF0044?style=flat-square&logo=youtubemusic&logoColor=white)](#)

Stream millions of songs — **no login, no premium required.**

| Feature | Detail |
|---|---|
| Home Feed | Quick Picks · Daily Discover · Keep Listening · Forgotten Favorites · Meld-style animated sliders |
| Artist Discovery | Browse via `ChannelTabInfo` integration, song-only filtering |
| Mood Playlists | Spotify-style curated mood screens with favorites |
| Smart Search | Crossfade animations + auto-focus + expandable search bar |
| Auto-Next Radio | Continuous Meld-style radio queues |
| Artwork | High-res `maxresdefault` + shimmer loading skeletons + HQ thumbnails |
| Trending | ![Last.fm](https://img.shields.io/badge/Last.fm-API-D51007?style=flat-square&logo=lastdotfm&logoColor=white) with iTunes RSS fallback |
| Player | Unified online + offline music player with stream badge + ExoPlayer streaming |

---

## 🌐 &nbsp;Network Streaming — NEW in v1.1.4

Stream directly from your own servers — no cloud needed.

| Feature | Detail |
|---|---|
| Protocols | **SMB / FTP / WebDAV** — browse & play via local proxy (mpvRex-style) |
| UI | Brand-new **Network tab** · floating bottom bar · video card design · scrollable Add Server dialog |
| Stream Link | Paste any **HTTP / HLS / DASH / SmoothStreaming** URL and play instantly |
| Engine | Robust HTTP source + proxy streaming — remote files play reliably |
| Fixes | Re-scan fixes, duplicate recent links fixed, SMB/WebDAV/FTP playback repaired |

---

## ▶ &nbsp;Local Playback

### Playback — Rebuilt in v1.1.4
▸ **Media3 + MPV** dual-engine support  
▸ **Music & Video** — full-featured media player  
▸ **Modern Player & Wavy Player** UI styles  
▸ **mpvRex/MPVEx-style controls** with redesigned Settings sheet (Controls / Aesthetics / Gestures / Advanced)  
▸ **Subtitle & Audio track panel** — ExoPlayer track selection  
▸ **Animated seekbars** — Classic, Squiggly, or Wavy  
▸ **Subtitles** — built-in SRT parser  
▸ **Speed control** — 0.25× to 2×  
▸ **Aspect ratio** — fit, fill, stretch & more · keep fitted & centered across rotation  
▸ **Volume boost up to 200%** (video + music) with percent indicator  
▸ **MX/VLC-style pinch zoom** — EMA smoothing, dead zone, 8.0 sensitivity  
▸ **Gesture rework** — volume / brightness / zoom never drop touches  
▸ **Landscape right-side panel** with live preview · portrait sheet overlays without squishing video  
▸ **Shuffle & Repeat** — all modes  

### Library
▸ **Folders view** — browse like your file manager  
▸ **Video playlists & favorites** — mpvRex-style Library view model  
▸ **Playlists** — create and manage  
▸ **Audio mode** — background playback from video files  
▸ **Smart search** — instant library lookup  
▸ **Sort & filter chips** — name, date, size, duration  
▸ **Video/Folder segmented toggle** on home top bar  

### UI & Experience
▸ Material 3 — expressive motion, tonal elevation  
▸ **BloomeeTunes-style animated nav** — ripple, expanding pill, fade & scale transitions  
▸ Mini player while browsing  
▸ **28 handcrafted offline themes**  
▸ Grid & List view toggle  
▸ **Font changer** — built-in fonts + TTF/OTF importer *(NEW)*  
▸ **Splash screen** — animated vector logo, no black screen *(FIXED in v1.1.4)*  
▸ **No ads. Ever.**  
▸ In-app update checker via GitHub Releases (Settings → Updates)  
▸ R8 code + resource shrinking — smaller APK (22.7 MB)  

---

## ◈ &nbsp;Themes

28 fully offline themes — works on any Android 5+ device. No dynamic color, no wallpaper dependency.

> Switch from **Settings → Appearance → Theme**

| Theme | Preview | Mood |
|---|---|---|
| Midnight | ![#0A0E1A](https://placehold.co/50x18/0A0E1A/0A0E1A) | Deep dark blue-black |
| AMOLED | ![#000000](https://placehold.co/50x18/000000/000000) | Pure black for OLED |
| Ocean | ![#071825](https://placehold.co/50x18/071825/40E0D0) | Deep teal depths |
| Forest | ![#071510](https://placehold.co/50x18/071510/4CAF50) | Rich dark greens |
| Rose | ![#1A0810](https://placehold.co/50x18/1A0810/FF80AB) | Soft rose glow |
| Amber | ![#1A1000](https://placehold.co/50x18/1A1000/FFB300) | Warm golden hour |
| Lavender | ![#150D1F](https://placehold.co/50x18/150D1F/CE93D8) | Dreamy purple |
| Neon Lime | ![#0A1A00](https://placehold.co/50x18/0A1A00/CCFF00) | Electric energy |
| Jade Mist | ![#001A12](https://placehold.co/50x18/001A12/00E5A0) | Cool jade calm |
| Magenta Pulse | ![#1A0015](https://placehold.co/50x18/1A0015/FF00CC) | Vivid magenta burst |
| Deep Indigo | ![#080B1A](https://placehold.co/50x18/080B1A/5C6BC0) | Classic depth |
| **+ 17 more** | | All included, all offline |

All themes use **WCAG-compliant contrast** — always readable, no eye strain.

---

## 📸 &nbsp;Screenshots

<div align="center">
<img src="assets/screenshots/01-videos-tab.jpg" width="24%" alt="Videos"/>
<img src="assets/screenshots/02-music-tab.jpg" width="24%" alt="Music"/>
<img src="assets/screenshots/03-network-tab.jpg" width="24%" alt="Network"/>
<img src="assets/screenshots/04-home-feed.jpg" width="24%" alt="Home"/>
</div>

<div align="center">
<img src="assets/screenshots/05-settings-theme.jpg" width="24%" alt="Settings - Theme"/>
<img src="assets/screenshots/06-settings-player.jpg" width="24%" alt="Settings - Player"/>
<img src="assets/screenshots/07-player-subtitle.jpg" width="24%" alt="Player - Subtitle"/>
<img src="assets/screenshots/08-player-controls.jpg" width="24%" alt="Player - Controls"/>
</div>

<div align="center">
<img src="assets/screenshots/09-player-landscape.jpg" width="48%" alt="Player - Landscape"/>
</div>

---

## 📥 &nbsp;Download

| Version | Date | Size | Downloads | Link |
|---|---|---|---|---|
| **v1.1.4** *(latest)* | 2026-08-29 | 22.7 MB | 28 | [VidMax-v1.1.4-release.apk](https://github.com/HATAKE2008/vidamx/releases/download/v1.1.4/VidMax-v1.1.4-release.apk) |
| v1.1.3 | 2026-08-12 | 33.2 MB | 12 | [VidMax-v1.1.3-release.apk](https://github.com/HATAKE2008/vidamx/releases/download/v1.1.3/VidMax-v1.1.3-release.apk) |
| v1.1.1 | 2026-08-07 | 32.4 MB | 22 | [VidMax-v1.1.1-release.apk](https://github.com/HATAKE2008/vidamx/releases/download/v1.1.1/VidMax-v1.1.1-release.apk) |
| v1.1.0 | 2026-08-07 | 32.4 MB | 5 | [VidMax-v1.1.0-release.apk](https://github.com/HATAKE2008/vidamx/releases/download/v1.1.0/VidMax-v1.1.0-release.apk) |

> **All releases:** [github.com/HATAKE2008/vidamx/releases](https://github.com/HATAKE2008/vidamx/releases)

**Install:** Download APK → Allow "Install from unknown sources" → Open and update over existing install to keep data. In-app update checker: `Settings → Updates`.

---

## ⬡ &nbsp;Architecture

```
UI  (Jetpack Compose)
     │
ViewModel
     │
Repository
     │
Data & Services
(MPV · MediaSession · Last.fm · ContentResolver · Network Proxy)
```

**Tech Stack**

| Technology | Role |
|---|---|
| ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white) | Language — 100% |
| ![Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white) | UI toolkit + Material 3 |
| ![MVVM](https://img.shields.io/badge/MVVM-Clean%20Architecture-6366F1?style=flat-square) | Architecture pattern |
| ![Media3](https://img.shields.io/badge/Media3-ExoPlayer-0F9D58?style=flat-square&logo=google&logoColor=white) | Primary media engine |
| ![MPV](https://img.shields.io/badge/MPV-JNI%20Bridge-111111?style=flat-square) | Secondary media engine |
| ![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?style=flat-square&logo=gradle&logoColor=white) | Build system |
| ![Actions](https://img.shields.io/badge/GitHub%20Actions-CI%2FCD-2088FF?style=flat-square&logo=githubactions&logoColor=white) | Automation (auto-build + sign + release on tag) |

---

## ⌗ &nbsp;Project Structure

```
app/src/main/java/com/vidmax/player/
├── data/
│   ├── model/       ← VideoItem, AudioItem, OnlineSong
│   └── repository/  ← Video, Audio, OnlineMusic repos
├── service/
│   ├── PlaybackService.kt
│   └── AudioService.kt
├── ui/
│   ├── components/  ← MiniPlayer, SearchBar, SortChips
│   ├── online/      ← OnlineMusic, MoodPlaylist screens
│   ├── network/     ← Network tab, SMB/FTP/WebDAV + Stream Link  (NEW)
│   ├── player/      ← PlayerActivity, AnimatedSlider, Track Panels
│   ├── screen/      ← Home, Folders, Playlist, Settings (+ Font picker)
│   └── theme/       ← Material 3 Color + 28 Themes
├── utils/
│   └── SubtitleParser.kt  ·  LastFmClient.kt  ·  NetworkProxy
└── viewmodel/
    ├── LibraryViewModel.kt
    ├── MusicHomeViewModel.kt
    ├── NetworkViewModel.kt  (NEW)
    └── PlayerViewModel.kt
```

---

## ↗ &nbsp;Getting Started

**Requirements**

![Studio](https://img.shields.io/badge/Android%20Studio-Hedgehog+-3DDC84?style=flat-square&logo=androidstudio&logoColor=white)
![SDK](https://img.shields.io/badge/SDK-21+-3DDC84?style=flat-square&logo=android&logoColor=white)
![NDK](https://img.shields.io/badge/NDK-Required%20for%20MPV-EA580C?style=flat-square&logo=android&logoColor=white)

```bash
git clone https://github.com/HATAKE2008/vidamx.git
cd vidamx
./gradlew assembleDebug
```

Or download the latest APK → [**Releases**](https://github.com/HATAKE2008/vidamx/releases) · Direct: [**v1.1.4 APK**](https://github.com/HATAKE2008/vidamx/releases/download/v1.1.4/VidMax-v1.1.4-release.apk)

---

## ⇄ &nbsp;Contributing

1. Fork → Feature branch → Commit → Pull Request
2. Follow existing Kotlin style and Material 3 guidelines

Found a bug? Open an [**Issue**](https://github.com/HATAKE2008/vidamx/issues) with device info, steps to reproduce, and logcat output.

---

## ◻ &nbsp;License

MIT © 2026 HATAKE2008 — see [LICENSE](./LICENSE)

---

## ◇ &nbsp;Acknowledgements

[![Media3](https://img.shields.io/badge/Jetpack%20Media3-Primary%20Engine-0F9D58?style=flat-square&logo=google&logoColor=white)](https://developer.android.com/media/media3)
[![MPV](https://img.shields.io/badge/MPV-Secondary%20Engine-111111?style=flat-square)](https://mpv.io/)
[![Compose](https://img.shields.io/badge/Compose-UI%20Toolkit-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material3](https://img.shields.io/badge/Material%203-Design%20System-009688?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![Lastfm](https://img.shields.io/badge/Last.fm-Metadata-D51007?style=flat-square&logo=lastdotfm&logoColor=white)](https://www.last.fm/api)

---

<img src="https://capsule-render.vercel.app/api?type=waving&color=0A0E1A,071825&height=100&section=footer" width="100%"/>

<div align="center">

Made with ♥ in Kotlin

*Saved from a sketchy ad-filled app? Give it a* ⭐

</div>
