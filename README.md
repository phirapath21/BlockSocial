# Satoshi Dashboard (Blockclock Widget) 🪙🕒

[![Android Build](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

A premium, feature-rich Android app and Home Screen widget suite inspired by the iconic hardware **E-Ink Blockclocks**. Keep track of Bitcoin spot price, mempool transaction fees, halving counts, Satoshi purchasing power, block height, and MicroStrategy (MSTR) stock statistics directly from your home screen. Designed for high visual fidelity, seamless data synchronization, and elegant custom styling.

---

## ✨ Premium Features

### 1. Resizable & Interactive Widgets
- **Resizable Layouts**: Widgets default to a 2x1 grid size but can be resized freely (horizontally and vertically) on your launcher.
- **Three Unique Widget Types**:
  - 🪙 **Blockclock Widget** — Cycles through 13 active Bitcoin/MSTR price and network metrics.
  - 💬 **Quotes Widget** — Rotates classic, inspiring Cypherpunk and Bitcoin quotes (Satoshi Nakamoto, Hal Finney, Eric Hughes).
  - ⏳ **Halving Countdown Progress Bar** — Renders a block-filled ASCII progress bar (`[████████░░░░░░░]`) showing percent progress, blocks remaining, or estimated days.
- **Tap to Cycle**: Tap the widgets to cycle through modes or quotes on the fly.
- **Custom Refresh Intervals**: Configure background updates (15m, 30m, 60m) inside settings to optimize battery life. Powered by WorkManager: Doze-friendly, runs only when online, and survives reboots.
- **Haptic Feedback**: Soft vibration feedback on taps confirms mode selection changes. Respects global toggle.
- **Stale Data Warning Badge**: Displays a warning icon `(!)` if the BTC price or block height could not be refreshed. Other sources fail independently and keep their last known value.

### 2. The 13 Information Modes (Blockclock Widget)
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

### 3. Active Mode Customization
- Enable or disable specific Blockclock modes inside settings to customize your widget cycling.

### 4. E-Ink Blockclock Design
The widgets and the app share one look, modelled on small e-ink blockclock hardware:
- **Black device body** with a row of **individual e-ink screens**, one character per screen.
- **Stacked label screen** on the left (e.g. `BTC` over `USD`) and an optional **unit screen** on the right (e.g. `MIL`, `EH/S`).
- Values are padded with blank screens to a seven-screen device; decimal points ride with their digit.
- **Moscow Time** reads like a clock face (1,029 sats → `10:29`).
- Halving widget adds a thin e-ink **epoch progress bar**; the Quotes widget is a single large e-ink screen.

---

## 🛠️ Tech Stack & Architecture

- **Core UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material Design 3.
- **Background scheduling**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) periodic work with a network constraint and exponential backoff retries.
- **Widget Engine**: Standard Android `AppWidgetProvider` paired with custom remote views tinting.
- **Concurrency & Network**: Kotlin Coroutines; all API sources are fetched in parallel and merged per-field with the cache.
- **Storage**: `SharedPreferences` for caching widget modes, selected themes, and API data.
- **API Integrations**:
  - **Coinbase Spot API** (BTC Price & THB Rate)
  - **Yahoo Finance v8 API** (MSTR Price)
  - **Mempool.space API** (Block Height, Fees, Adjustment, Hashrate, Lightning Capacity)
  - **Blockchain.info API** (Circulating Supply Fallback)

---

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
   - Scroll down to find **Satoshi Dashboard** (or **Blockclock**).
   - Drag any of the resizable widgets (Blockclock, Quotes, or Halving Countdown) onto your home screen!

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
│   │   │       │   ├── btc_widget_blockclock.xml # Blockclock Widget layout (XML layout)
│   │   │       │   ├── btc_widget_quote.xml      # Quotes Widget layout (XML layout)
│   │   │       │   └── btc_widget_halving.xml    # Halving Widget layout (XML layout)
│   │   │       └── drawable/                # Theme background resources
│   │   └── build.gradle.kts
└── settings.gradle.kts
```

---

## 📜 License

This project is licensed under the MIT License. Feel free to use, modify, and build upon it!
