# Blockface 🪙🕒

*Bitcoin at a glance — a clock face for the blockchain on your home screen.*

[![Android Build](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

A premium, feature-rich Android app and Home Screen widget suite inspired by desk-top hardware Bitcoin block clocks. Keep track of Bitcoin spot price, mempool transaction fees, halving counts, Satoshi purchasing power, block height, and MicroStrategy (MSTR) stock statistics directly from your home screen. Designed for high visual fidelity, seamless data synchronization, and elegant custom styling.

---

## 💬 Nostr social

Blockface is also a Nostr client with a familiar timeline layout.

- **Layout:** bottom bar (icons) Home · Search · Bitcoin · Notifications · Messages; a side bar (swipe right from the left, or tap your avatar) holds Profile, Widgets, Price alerts and Settings.
- **Private messages (NIP-17):**
  - End-to-end encrypted with NIP-44, implemented in plain Kotlin (ChaCha20, HKDF, HMAC) and checked against the official vectors.
  - Messages are gift-wrapped (kind 1059) and delivered to each recipient's inbox relays (kind 10050). You get a copy too.
  - You can start a chat from ✉ in Messages or on a profile. Group chats work by participant set.
  - With Amber, messages are sealed and opened through the signer (nip44_encrypt/decrypt).
  - Only encrypted wraps are cached on disk.
- **Accounts:** connect a **signer app** (NIP-55, e.g. Amber; recommended, the key never enters Blockface), paste an **nsec** (encrypted with an Android Keystore key, excluded from backup, deleted on logout), or **create a new account** (the nsec is shown once to save). Screens showing a key block screenshots.
- **Timeline:** notes and reposts from people you follow, pull to refresh, infinite scroll; logged out, a public #bitcoin feed.
- **Notes:** reply (NIP-10 threads), repost (kind 6), quote (NIP-18 `q` tag), like (kind 7), zap (NIP-57, pays through your Lightning wallet app), share link, delete your own (NIP-09).
- **Profiles:** banner, avatar, bio, NIP-05, Lightning address, follow/unfollow (kind 3, keeps your other follows), edit your own (kind 0).
- **Notifications:** replies, mentions, reposts, likes and zaps.
- **Timeline basics:**
  - New notes wait behind a "↑ N new posts" pill instead of shifting what you're reading.
  - Profiles have *Posts*, *Replies* and *Media* tabs.
  - Images open full screen, laid out like X:
    - top: back, and ⋮ with *Save image* (to Pictures/Blockface) and *Open in browser*;
    - bottom: the author with Follow, the note's text, Reply · Repost · Like · Bookmark · Share, and a "Post your reply" bar;
    - pinch or double-tap to zoom.
  - The post screen has X's pinned "Post your reply" bar: type in place, add a photo, or ⤢ for the full composer.
  - Bookmarks are a public NIP-51 list, from ⋯ › Bookmark, listed in the side bar.
  - Typing `@` suggests people and links them as `nostr:npub`.
  - Content warnings (NIP-36) hide notes behind "Show", and ⚠ in compose marks your own.
- **Unread and alerts:**
  - Badges on Notifications and Messages, and bold unread conversations.
  - Phone notifications for replies, mentions, zaps and DMs while the app is closed, from the same background refresh as the price alerts (every 15/30/60 min, not instant). You can turn them off in Settings › Nostr.
  - Tapping a notification opens the right tab.
- **Follow lists:**
  - Tap "Following" or "Followers" on a profile to see the list, with follow buttons.
  - Followers come from the indexers and are capped at 500.
- **Safety:** mute accounts (NIP-51 mute list) and report notes (NIP-56); in-app Community rules (`docs/community-rules.md`).
- **Search:** people, posts and #hashtags via NIP-50 search relays; an npub, note id, nevent or name@domain (NIP-05) opens directly.
- **Photos:** attach up to 4 images per post with the system photo picker. They're re-encoded on the phone (EXIF and GPS removed), uploaded to Blossom (your kind-10063 servers or blossom.primal.net), and posted with NIP-92 `imeta` tags.
- **Relays:** relay.damus.io, nos.lol and relay.primal.net by default, plus your NIP-65 relays when you have them, or your own list in Settings. Profiles and lists also come from indexer relays (purplepag.es, user.kindpag.es, indexer.coracle.social).
- **Speed:**
  - A query returns once the first relay finishes, plus a short grace period, instead of waiting for the slowest relay. Relays that are down aren't waited for.
  - Names, avatars and counts fill in after the notes appear.
  - The last timeline, profiles and your lists are cached on disk, so the app opens instantly.

Code: `nostr/core` is plain Kotlin (events, NIP-19, Schnorr via ACINQ's secp256k1, relay protocol, zaps) with unit tests; `nostr/` holds the signers, key storage, the OkHttp relay pool and the repository; `ui/social` holds the screens.


Planned work: see [`docs/TODO.md`](docs/TODO.md).
---

## ✨ Premium Features

### 1. Resizable & Interactive Widgets
- **Resizable Layouts**: Widgets default to a 2x1 grid size but can be resized freely (horizontally and vertically) on your launcher.
- **Three Unique Widget Types**:
  - 🪙 **Bitcoin Metrics Widget** — Cycles through 13 active Bitcoin/MSTR price and network metrics.
  - 📈 **Price Chart Widget** — BTC price, change and a 24H / 7D sparkline; tap to switch range.
  - 💬 **Quotes Widget** — Rotates classic, inspiring Cypherpunk and Bitcoin quotes (Satoshi Nakamoto, Hal Finney, Eric Hughes).
  - ⏳ **Halving Countdown Progress Bar** — Renders a block-filled ASCII progress bar (`[████████░░░░░░░]`) showing percent progress, blocks remaining, or estimated days.
- **Tap to Cycle**: Tap the widgets to cycle through modes or quotes on the fly.
- **Custom Refresh Intervals**: Configure background updates (15m, 30m, 60m) inside settings to optimize battery life. Powered by WorkManager: Doze-friendly, runs only when online, and survives reboots.
- **Stale Data Warning Badge**: Displays a warning icon `(!)` if the BTC price or block height could not be refreshed. Other sources fail independently and keep their last known value.

### 2. The 13 Information Modes (Bitcoin Metrics Widget)
1. **BTC Price** — Spot price of Bitcoin (formatted as `X.xx mil` in Thai Baht or with symbol in USD).
2. **Sats per Currency** — SATS per 1 USD/THB (Moscow Time style).
3. **Block Height** — Current Bitcoin block height.
4. **Moscow Time (Clean)** — Clean, distraction-free SATS-per-currency layout.
5. **MSTR Holdings** — MicroStrategy's total Bitcoin holdings.
6. **MSTR / BTC Ratio** — Share price to BTC ratio.
7. **MSTR Stock Price** — MicroStrategy share price.
8. **BTC Circulation** — Total circulating Bitcoin supply in millions (e.g. `19.850 MIL`).
9. **Halving Countdown** — Blocks remaining until the next Bitcoin halving.
10. **Mempool Fees** — Recommended transaction fees (Fastest/Mid/Low sat/vB).
11. **Network Hash Rate** — Estimated network hashrate (3d) in EH/s.
12. **Difficulty Adjustment** — Difficulty change percentage and retarget epoch progress.
13. **Lightning Capacity** — Public Lightning Network capacity in BTC.

### 3. App Layout
Four tabs along the bottom:
- **Dashboard** — a hero panel (STACK / BLOCK / PRICE / HALVING / SATS / FEES) and a BTC price chart (24H / 7D, drag to read any hour). Pull down to refresh.
- **Share** — under the hero panel: sends the panel as an image (in the current theme) plus a one-line post, such as `Bitcoin: $74,455 (+1.2% in 24h) · block 925,000 #bitcoin`, to any app through the share sheet. Pick a Nostr client (Amethyst, Primal, …) to post it as a note others can quote or repost. The app holds no Nostr keys and sends nothing itself; your stack is never shareable.
- **Widgets** — how to add widgets, then every metric as a panel in four collapsible sections (**Market**, **Network**, **Supply & Lightning**, **Strategy (MSTR)**, plus **Your stack**). Each panel has an **On widget** switch; hold and drag a panel to reorder it within its section — that order is the order widget taps cycle through.
- **Settings** — your stack, display currency, refresh interval, **data sources** (BTC price from Coinbase or Kraken; THB converted or from Bitkub) and about.

### 3a. Your Stack
- Enter how much bitcoin you hold (in BTC or sats) under **Settings > Your stack**; it is stored only on the device.
- A **STACK** panel and widget mode show what it is worth in USD or THB, with the amount and 24h change as the caption. It can also be the dashboard's hero.

### 3b. Price Alerts
- **BTC rises above / falls below** a price (USD or THB) and **fees at or below** a sat/vB level, set in Settings.
- **Network events:** a day before the next halving and before each difficulty adjustment (with the estimated change).
- Notifies once per crossing, then re-arms when the condition turns false again; stale data never triggers an alert.
- Checked with each background refresh (15/30/60 min), even with no widget on the home screen. Android 13+ asks for notification permission when you switch an alert on.

### 4. Design
The widgets and the app share one look: a **dark grained panel** with **tall condensed type**.
- A **stacked label** beside the value (e.g. `BTC` over `USD` with a rule between), the **value in large type**, and an optional **unit** set high on the right (e.g. `MIL`, `EH/S`).
- A **monospace caption** underneath (e.g. `Market price of bitcoin`); a red dot marks stale data.
- The panel fills the widget and is painted as one image, so it stays crisp at every size; the app uses the same painter.
- **Retro theme** (Settings → Display → Theme): green phosphor terminal type with a soft glow, scanlines and faint falling hex behind the value; stale data is marked in amber. It applies to the app and every widget.
- **Orange pill theme**: glossy bitcoin-orange panels with near-black type, set in a warm dark app; stale data is marked with a white dot.
- Display face: [Oswald](https://github.com/googlefonts/OswaldFont) Light, bundled under the SIL Open Font License (see `licenses/Oswald-OFL.txt`).
- Retro theme face: [VT323](https://fonts.google.com/specimen/VT323), subset to Latin, bundled under the SIL Open Font License (see `licenses/VT323-OFL.txt`).

---

## 🛠️ Tech Stack & Architecture

- **Core UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material Design 3.
- **Background scheduling**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) periodic work with a network constraint and exponential backoff retries.
- **Widget Engine**: Standard Android `AppWidgetProvider` paired with custom remote views tinting.
- **Concurrency & Network**: Kotlin Coroutines; all API sources are fetched in parallel and merged per-field with the cache.
- **Storage**: `SharedPreferences` for caching widget modes, selected themes, and API data.
- **API Integrations**:
  - **Coinbase** (BTC price, USD/THB rate, 24h stats, price history) or **Kraken** (BTC price)
  - **Bitkub** (optional BTC/THB market price and 24h change)
  - **Yahoo Finance v8 API** (MSTR Price)
  - **Mempool.space API** (Block Height, Fees, Adjustment, Hashrate, Lightning Capacity)
  - **Blockchain.info API** (Circulating Supply Fallback)

---

## 📦 Release Build

Step-by-step guide: [`docs/RELEASE.md`](docs/RELEASE.md). Store listing text, graphics and policy-form answers: [`docs/play-store/`](docs/play-store/). Privacy policy: [`docs/privacy-policy.md`](docs/privacy-policy.md).

- **Application ID:** `io.github.phirapath21.btcwidget` (the Kotlin package stays `com.example.btcwidget`).
- **Signing:** create a keystore, then add a git-ignored `keystore.properties` at the project root:
  ```properties
  storeFile=release.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```
  Without it, `./gradlew assembleRelease` produces an unsigned APK.
- **Shrinking:** release builds run R8 with resource shrinking; app rules live in `app/proguard-rules.pro`.

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Koala or newer recommended)
- **JDK 17** (or standard Java SDK configuration)
- An Android device or emulator running **Android 8.0 (Oreo) / API Level 26** or higher.

### Installation & Build

1. **Clone the repository**:
   ```bash
   git clone https://github.com/phirapath21/btcwidget.git
   cd btcwidget
   ```

2. **Build the debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on device**:
   Connect your device via USB (with Developer Mode and USB debugging active) and run:
   ```bash
   ./gradlew installDebug
   ```

4. **Add the Widget**:
   - Go to your home screen.
   - Long press on an empty space, tap **Widgets**.
   - Scroll down to find **Blockface**.
   - Drag any of the resizable widgets (Bitcoin metrics, Halving countdown or Bitcoin quotes) onto your home screen!

---

## 📂 Project Structure

```
btcwidget/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/btcwidget/
│   │   │   │   ├── BtcWidgetProvider.kt      # Main AppWidgetProvider (2x1) & intent routing
│   │   │   │   ├── BtcQuoteWidgetProvider.kt # Quotes Widget AppWidgetProvider
│   │   │   │   ├── BtcHalvingWidgetProvider.kt# Halving Progress Bar Widget AppWidgetProvider
│   │   │   │   ├── WidgetUpdater.kt          # Centralized widget updates controller
│   │   │   │   ├── MainActivity.kt          # App entry point
│   │   │   │   ├── PriceData.kt             # Data classes & Network repository
│   │   │   │   ├── ui/main/
│   │   │   │   │   ├── MainScreen.kt        # Jetpack Compose dashboard UI
│   │   │   │   │   └── MainScreenViewModel.kt# Live data updates and caching
│   │   │   └── res/
│   │   │       ├── layout/
│   │   │       │   ├── btc_widget_blockclock.xml # Metrics & halving widget layout (one painted image)
│   │   │       │   ├── btc_widget_quote.xml      # Quotes Widget layout (XML layout)
│   │   │       │   └── btc_widget_halving.xml    # Halving Widget layout (XML layout)
│   │   │       └── drawable/                # Theme background resources
│   │   └── build.gradle.kts
└── settings.gradle.kts
```

---

## 📜 License

This project is licensed under the MIT License. Feel free to use, modify, and build upon it!
