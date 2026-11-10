# Blockface 🪙🕒

*Bitcoin at a glance — a clock face for the blockchain on your home screen.*

[![Android Build](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

A premium, feature-rich Android app and Home Screen widget suite inspired by desk-top hardware Bitcoin block clocks. Keep track of Bitcoin spot price, mempool transaction fees, halving counts, Satoshi purchasing power, block height, and MicroStrategy (MSTR) stock statistics directly from your home screen. Designed for high visual fidelity, seamless data synchronization, and elegant custom styling.

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
- **Dashboard** — a hero panel (STACK / BLOCK / PRICE / SATS / FEES) and a BTC price chart (24H / 7D, drag to read any hour). Pull down to refresh.
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
