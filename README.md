# Block

*Bitcoin at a glance, and the people who talk about it.*

[![Android CI](https://github.com/phirapath21/btcwidget/actions/workflows/android.yml/badge.svg)](https://github.com/phirapath21/btcwidget/actions/workflows/android.yml)
![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)

Block is an Android app with two halves:

- **Bitcoin widgets and dashboard.** Home-screen widgets inspired by desk blockclocks: price, sats per dollar or baht, block height, fees, halving countdown, hashrate, your stack's value and more, with price alerts.
- **A Nostr social client.** A timeline, replies, reposts, likes and zaps, private messages, lists, communities and search, laid out like X, with a Bitcoin-orange accent.

No account or server of ours is involved: market data comes from public APIs, social data from Nostr relays, and your keys stay on your phone or in your signer app.

---

## Contents

- [Bitcoin widgets](#bitcoin-widgets)
- [Dashboard and alerts](#dashboard-and-alerts)
- [Nostr social](#nostr-social)
- [Privacy and security](#privacy-and-security)
- [Building](#building)
- [Testing and CI](#testing-and-ci)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [License](#license)

---

## Bitcoin widgets

Four resizable home-screen widgets (2×1 by default):

| Widget | What it shows |
|---|---|
| **Bitcoin metrics** | One metric at a time; tap to cycle through the ones you switched on. |
| **Price chart** | BTC price, 24h change and a 24H / 7D sparkline; tap to switch range. |
| **Halving countdown** | A block-filled progress bar with percent done, blocks or days left. |
| **Quotes** | Cypherpunk and Bitcoin quotes (Satoshi Nakamoto, Hal Finney, Eric Hughes…); tap for the next. |

**Metrics** (pick and order them in the app's Widgets page):

| Market | Network | Supply & Lightning | Strategy (MSTR) | You |
|---|---|---|---|---|
| BTC price (USD or THB) | Block height | Circulating supply | MSTR share price | Your stack's value |
| Sats per dollar / baht | Mempool fees (fast / mid / low) | Lightning capacity | MSTR BTC holdings | |
| Moscow time (clean) | Hashrate (3-day) | | MSTR / BTC ratio | |
| | Difficulty adjustment | | | |
| | Halving countdown | | | |

**Look:** a dark grained panel with tall condensed type, a stacked label beside the value and a monospace caption, painted as one image so it stays sharp at any size. Three themes, for the widgets only (the app itself keeps one dark look):

- **Classic:** white on a dark e-ink-style panel.
- **Retro:** green phosphor terminal type with glow, scanlines and faint falling hex; stale data in amber.
- **Orange pill:** glossy Bitcoin-orange panels with near-black type.

**Refresh:** every 15, 30 or 60 minutes with WorkManager (only when online, Doze-friendly, survives reboots). If a source fails, its value keeps the last good number and a red dot marks it stale; other sources carry on.

## Dashboard and alerts

The **Bitcoin** tab has a hero panel (Stack, Block, Price, Halving, Sats or Fees) and a BTC price chart (24H / 7D; drag to read any hour). Pull down to refresh.

- **Share:** sends the hero panel as an image plus a one-line post (`Bitcoin: $74,455 (+1.2% in 24h) · block 925,000 #bitcoin`) through Android's share sheet. Your stack is never offered for sharing.
- **Your stack:** enter what you hold, in BTC or sats. It's stored only on the phone and shown in USD or THB with its 24h change.
- **Price alerts:** BTC above or below a price, and fees at or below a sat/vB level. Network events too: the day before a halving, and before each difficulty adjustment with the estimated change. Each alert fires once per crossing and is never triggered by stale data.
- **Data sources:** BTC price from Coinbase or Kraken; THB converted or from Bitkub.

## Nostr social

**Layout:**
- **Bottom bar:** Home · Search · Bitcoin · Notifications · Messages.
- **Side bar** (swipe from the left or tap your avatar): Profile · Lists · Bookmarks · Communities · Widgets · Price alerts · Settings.

**Getting started:**
- **New here:** a welcome page offers three ways in: create an account, log in with a signer app, or paste an nsec.
- **Creating an account:** setup walks you through a name and picture, topics, people to follow, and saving your key.
- **Signer apps:** Amber (NIP-55) or a remote bunker (NIP-46), so the key never enters Block.
- **Several accounts:** switch from the side bar.

**Reading:**
- **Home feeds**, Primal-style, which you can add, remove and reorder:
  - Latest, or Latest with replies, from people you follow
  - Global, or a single relay
  - A #hashtag
  - A list of people
  - DVM "For you" feeds (NIP-90)
- **Moving through the feed:**
  - New notes wait behind a "↑ N new posts" pill instead of shifting what you're reading.
  - Feeds keep their scroll position when you switch tabs or come back.
  - Long posts are cut short with *Read more*.
- **Threads:** nested replies. On the post page, a floating *Post your reply* bar sits above the keyboard.
- **Media:**
  - Images, GIFs and videos are shown inline.
  - Swipe between photos full screen, pinch to zoom.
  - The video player has autoplay settings (always, Wi-Fi only, never), quality choice, data saver, and full-screen landscape.
- **Links:**
  - Link previews in notes.
  - Links open in Block's own browser, with the post underneath, like X; a setting sends them to your phone's browser instead.
- **More to read:** long-form articles (NIP-23), polls (NIP-88), on-device translation (ML Kit), content warnings (NIP-36), and custom emoji (NIP-30).

**Writing:**
- Notes with up to 4 photos or a video, uploaded to Blossom servers with EXIF/GPS stripped from photos.
- `@` mentions and hashtag autocomplete.
- Drafts and scheduled posts.
- Quotes, reposts and emoji reactions.
- Content warnings on your own notes.

**People and lists:**
- **Profiles:** Posts, Replies, Media and Articles tabs, verified NIP-05 ✓, badges (NIP-58), and a profile QR code you can show or scan.
- **Following:** follow lists and followers, plus who-to-follow suggestions.
- **Lists of people** (NIP-51), like X's Lists:
  - make them from the side bar, or add someone from their profile with ☰+;
  - read one as a feed, or pin it to Home.

**Talking:**
- **Private messages (NIP-17):**
  - NIP-44 encryption implemented in plain Kotlin and checked against the official test vectors.
  - Gift-wrapped and sent to each recipient's inbox relays.
  - Photos, replies and reactions inside chats.
  - Group chats.
  - Message requests from people you don't follow.
  - Older NIP-04 messages are shown, marked as an old format.
- **Communities and chats:** communities (NIP-72), public chats (NIP-28) and relay-based groups (NIP-29).
- **Zaps (NIP-57):** pay through your Lightning wallet app, or in one tap with Nostr Wallet Connect (NIP-47). The notes screen shows who liked, reposted and zapped.

**Notifications:**
- Likes, reposts and zaps of the same note are grouped ("Alice and 5 others liked your note").
- Unread badges.
- Phone notifications while the app is closed. These are checked on each background refresh, so they're not instant.

**Safety:**
- Mute accounts, words, hashtags and threads (NIP-51).
- Report notes (NIP-56).
- An optional web-of-trust filter that hides strangers' replies.
- In-app [community rules](docs/community-rules.md).

**Relays:**
- **Defaults:** relay.damus.io, nos.lol and relay.primal.net, plus your NIP-65 relays, or your own list.
- **Outbox model:** each person's notes are also read from the relays they write to.
- **Relay status page:** shows which relays are connected, slow or failing, with Retry.

**Speed:**
- Events are kept in a local SQLite database, so the app opens instantly.
- Queries return once the first relay answers instead of waiting for the slowest.
- Names, avatars and counts fill in after the notes appear.

## Privacy and security

- **Private keys and wallet links** (nsec, Nostr Wallet Connect) are encrypted with an Android Keystore key, excluded from backup and never logged. Screens that show a key block screenshots.
- **No analytics and no ads.** There is no Block server.
- **Everything you post on Nostr is public,** except private messages. That includes lists, bookmarks, mute lists, reactions and poll votes.
- **The full picture**, including every outside service the app talks to and why: [`docs/privacy-policy.md`](docs/privacy-policy.md).

## Building

**You need:**
- Android Studio (recent stable), or JDK 17+ and the Android SDK (compile SDK 36).
- A phone or emulator on Android 7.0 (API 24) or newer.

```bash
git clone https://github.com/phirapath21/btcwidget.git
```
```bash
cd btcwidget
```
```bash
./gradlew installDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`. Or open the folder in Android Studio and press Run; Gradle downloads every library on the first sync.

**Adding a widget:** long-press the home screen, tap **Widgets**, find **Block**, and drag one out.

**Release builds:**
- **Application ID:** `io.github.phirapath21.btcwidget` (the Kotlin package stays `com.example.btcwidget`).
- **Signing:** add a git-ignored `keystore.properties` at the project root. Never commit it or the keystore.
  ```properties
  storeFile=release.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```
  Without this file, `./gradlew assembleRelease` makes an unsigned APK.
- **Shrinking:** release builds use R8 with resource shrinking; the rules are in `app/proguard-rules.pro`.
- **Step by step:** see [`docs/RELEASE.md`](docs/RELEASE.md).

## Testing and CI

```bash
./gradlew testDebugUnitTest
```
```bash
./gradlew connectedDebugAndroidTest
```

- **Unit tests** cover:
  - the Nostr core: events, NIP-19, Schnorr signatures, NIP-44 and NIP-17, zaps, lists, communities, the local database;
  - price parsing and merging, the widget painters and chart math, alerts and share text.
- **UI tests** run on a device or emulator.
- **GitHub Actions** ([`.github/workflows/android.yml`](.github/workflows/android.yml)) runs on every pull request and push to `main`:
  - unit tests, lint (reported only), a debug build and a release build with R8;
  - then the UI tests on an emulator.
  - The debug APK can be downloaded from each run's **Artifacts**.
- **APK size report:** after every release build (so on every CI run) a table shows what takes the room: code, native libraries per CPU type, resources, and code by library (Compose, ExoPlayer, ML Kit…). It's printed in the build log and on the run's **Summary** page, and saved to `app/build/reports/apk-size.md`. Locally: `./gradlew assembleRelease`.

## Project structure

```
app/src/main/java/com/example/btcwidget/
├── *WidgetProvider.kt, WidgetUpdater.kt   home-screen widgets
├── Blockclock*.kt, ChartPainter.kt        the painted widget panels
├── PriceData.kt, PriceSources.kt          market and network data, merged per field
├── PriceRefreshWorker.kt                  background refresh, alerts, Nostr notifications
├── alerts/                                price and network alerts
├── Navigation.kt, NavigationKeys.kt       screens (Navigation 3)
├── ui/main/                               Bitcoin dashboard, widgets page, settings
├── ui/social/                             Nostr screens and the social view model
└── nostr/
    ├── core/                              plain Kotlin: events, NIPs, crypto, relay protocol
    ├── RelayPool.kt                       OkHttp WebSocket relay connections
    ├── NostrRepository.kt                 account, cache and every Nostr action
    ├── EventStore.kt                      local SQLite event database
    └── Signers.kt, BunkerSigner.kt         nsec (Keystore), Amber and bunker signers
```

`nostr/core` has no Android dependencies, so it's unit-tested on the JVM.

**Main libraries:**
- **UI and navigation:** Jetpack Compose (Material 3) and Navigation 3.
- **Background and network:** WorkManager, OkHttp and Kotlin coroutines.
- **Signatures:** ACINQ secp256k1.
- **Media:** Coil 3 for images and GIFs; Media3 ExoPlayer for video, including HLS.
- **On-device features:** ML Kit for translation; ZXing and the Google code scanner for QR codes.

**Data sources:**
- **Market:** Coinbase, Kraken, Bitkub, Yahoo Finance (MSTR).
- **Network:** mempool.space, with blockchain.info as a fallback for supply.

## Documentation

| | |
|---|---|
| [`docs/TODO.md`](docs/TODO.md) | What's done and what's planned |
| [`docs/privacy-policy.md`](docs/privacy-policy.md) | Privacy policy |
| [`docs/community-rules.md`](docs/community-rules.md) | Community rules |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Building and publishing a release |
| [`docs/play-store/`](docs/play-store/) | Store listing, graphics and policy-form answers |

## License

This project is licensed under the MIT License.

Fonts: [Oswald](https://github.com/googlefonts/OswaldFont) and [VT323](https://fonts.google.com/specimen/VT323), both under the SIL Open Font License (see [`licenses/`](licenses/)).
