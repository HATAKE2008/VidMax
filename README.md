<div align="center">

<br/>

```
██╗   ██╗██╗██████╗ ███╗   ███╗ █████╗ ██╗  ██╗
██║   ██║██║██╔══██╗████╗ ████║██╔══██╗╚██╗██╔╝
██║   ██║██║██║  ██║██╔████╔██║███████║ ╚███╔╝ 
╚██╗ ██╔╝██║██║  ██║██║╚██╔╝██║██╔══██║ ██╔██╗ 
 ╚████╔╝ ██║██████╔╝██║ ╚═╝ ██║██║  ██║██╔╝ ██╗
  ╚═══╝  ╚═╝╚═════╝ ╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝
```

**A powerful, free and open-source video player for Android**  
*Powered by MPV · Built with Kotlin · Designed with Material 3*

<br/>

[![Build](https://github.com/HATAKE2008/VidMax/actions/workflows/build.yml/badge.svg)](https://github.com/HATAKE2008/VidMax/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Open%20Source-blue?style=flat-square)](#license)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/Material-3-teal?style=flat-square&logo=materialdesign)](https://m3.material.io)
[![Ad-Free](https://img.shields.io/badge/Ads-None%20Ever-red?style=flat-square)](#)
[![Themes](https://img.shields.io/badge/Themes-28%20Offline-orange?style=flat-square)](#-themes)

<br/>

</div>

---

## ✨ Why VidMax?

VidMax is built on a simple promise — **no ads, no tracking, no bloat.** Just a fast, beautiful video player that respects your device and your privacy. Under the hood runs the battle-tested **MPV engine**, one of the most capable media players ever built, wrapped in a clean Material 3 interface crafted entirely in Kotlin.

---

## 🎬 Features

### Playback
- **MPV-powered engine** — hardware-accelerated, codec-rich, format-versatile
- **Multiple player UIs** — choose between Modern Player and Wavy Player styles
- **Subtitle support** — built-in subtitle parser for SRT and more
- **Speed control** — 0.25× to 2× playback speed
- **Aspect ratio control** — fit, fill, stretch and more
- **Shuffle & Repeat** — all modes including repeat-one

### Library
- **Folders view** — browse videos by folder, just like your file manager
- **Playlist support** — create and manage your own playlists
- **Audio mode** — play audio from video files with background playback
- **Smart search** — find any file in your library instantly
- **Sort & filter chips** — sort by name, date, size, duration

### UI & Experience
- **Material 3 design** — expressive motion, modern typography, tonal elevation
- **Mini player** — keep playing while browsing your library
- **28 handcrafted themes** — fully offline, no internet required, instant switching
- **Grid & List view** — switch layouts on the fly
- **No ads. Ever.** — completely free, always

---

## 🎨 Themes

VidMax ships with **28 fully offline themes** — no dynamic color, no wallpaper dependency, just beautiful handcrafted color palettes that work on any device running Android 5+.

> Switch themes instantly from **Settings → Appearance → Theme**

<br/>

| Theme | Preview | Mood |
|---|---|---|
| **Midnight** | ![#0A0E1A](https://placehold.co/60x22/0A0E1A/0A0E1A) | Deep dark blue-black default |
| **AMOLED** | ![#000000](https://placehold.co/60x22/000000/000000) | Pure black for OLED screens |
| **Ocean** | ![#071825](https://placehold.co/60x22/071825/40E0D0) | Deep teal depths |
| **Forest** | ![#071510](https://placehold.co/60x22/071510/4CAF50) | Dark rich greens |
| **Rose** | ![#1A0810](https://placehold.co/60x22/1A0810/FF80AB) | Soft rose glow |
| **Amber** | ![#1A1000](https://placehold.co/60x22/1A1000/FFB300) | Warm golden hour |
| **Lavender** | ![#150D1F](https://placehold.co/60x22/150D1F/CE93D8) | Dreamy purple |
| **Neon Lime** | ![#0A1A00](https://placehold.co/60x22/0A1A00/CCFF00) | Electric green energy |
| **Jade Mist** | ![#001A12](https://placehold.co/60x22/001A12/00E5A0) | Cool jade calm |
| **Magenta Pulse** | ![#1A0015](https://placehold.co/60x22/1A0015/FF00CC) | Vivid magenta burst |
| **Deep Indigo** | ![#080B1A](https://placehold.co/60x22/080B1A/5C6BC0) | Classic indigo depth |
| **+ 17 more** | | All included, all offline |

<br/>

Every theme is built with **WCAG-compliant contrast ratios** — text is always readable, no eye strain.

---

## 📸 Screenshots

> Coming soon — contributions welcome!

---

## 🏗️ Architecture

VidMax follows a clean **MVVM architecture** with a unidirectional data flow:

```
UI Layer (Jetpack Compose)
    │
    ▼
ViewModel Layer
    │
    ▼
Repository Layer
    │
    ▼
Data / Service Layer (MPV, MediaSession, ContentResolver)
```

**Tech Stack:**

| Layer | Technology |
|---|---|
| Language | Kotlin 100% |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| Media Engine | MPV (via JNI bridge) |
| Audio Session | Android MediaSession / AudioService |
| Build System | Gradle (Kotlin DSL) |
| CI/CD | GitHub Actions |

---

## 📁 Project Structure

```
app/src/main/
├── java/com/vidmax/player/
│   ├── data/
│   │   ├── model/          ← VideoItem, AudioItem, FolderItem
│   │   └── repository/     ← VideoRepository, AudioRepository
│   ├── service/
│   │   ├── PlaybackService.kt   ← MPV playback session
│   │   └── AudioService.kt      ← Background audio
│   ├── ui/
│   │   ├── components/     ← MiniPlayer, VideoCard, SearchBar, SortChips
│   │   ├── player/         ← PlayerActivity, PlayerScreen
│   │   ├── screen/         ← Home, Folders, Playlist, Settings
│   │   │                      ModernPlayerScreen, WavyPlayerScreen
│   │   └── theme/          ← Material 3 Color + Theme
│   ├── utils/
│   │   └── SubtitleParser.kt
│   └── viewmodel/
│       ├── LibraryViewModel.kt
│       └── PlayerViewModel.kt
└── is/xyz/mpv/
    └── MPVLib.kt           ← MPV JNI bridge
```

---

## 🚀 Getting Started

### Requirements
- Android Studio Hedgehog or newer
- Android SDK 21+
- NDK (for MPV native library)

### Build

```bash
# Clone the repository
git clone https://github.com/HATAKE2008/VidMax.git
cd VidMax

# Build debug APK
./gradlew assembleDebug
```

Or download the latest APK from [**Releases**](https://github.com/HATAKE2008/VidMax/releases).

---

## 🤝 Contributing

Contributions are what make open-source beautiful. Any contribution you make is **greatly appreciated**.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

Please make sure your code follows the existing Kotlin style and Material 3 guidelines before submitting.

---

## 🐛 Bug Reports

Found a bug? Please open an [issue](https://github.com/HATAKE2008/VidMax/issues) with:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output (if available)

---

## 📜 License

VidMax is open-source software. See the [LICENSE](./LICENSE) file for details.

---

## 🙏 Acknowledgements

- [**MPV**](https://mpv.io/) — the powerful media engine at VidMax's core
- [**Jetpack Compose**](https://developer.android.com/jetpack/compose) — modern Android UI toolkit
- [**Material 3**](https://m3.material.io/) — Google's design system
- All contributors and testers who made this app better

---

<div align="center">

Made with ❤️ in Kotlin

*If VidMax saved you from a sketchy ad-filled video app, consider giving it a ⭐*

</div>
