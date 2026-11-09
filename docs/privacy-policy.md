# Blockface Privacy Policy

**Effective date:** 9 November 2026

Blockface ("the app") shows Bitcoin market and network data in an Android app and home-screen widgets. This policy explains what the app does with information. In short: **the app has no accounts, no ads, no analytics and no tracking, and it does not collect personal data.**

## Information stored on your device

The app keeps the following only on your device, in its private app storage:

- **Your settings** — display currency, refresh interval, data sources, which metrics the widget shows and their order, collapsed sections.
- **Your stack** — the amount of bitcoin you choose to enter, to show what it is worth. It is never sent anywhere by the app.
- **Alerts** — the price, fee and network-event alerts you set.
- **Cached market data** — the latest prices, network figures and hourly price history, so widgets and charts work offline.
- **Widget state** — the mode, range or quote each placed widget shows.

Uninstalling the app or clearing its storage deletes all of this.

**Android backup:** if you have Android backup turned on, Android may include the app's storage (including the items above) in your device backup to your own Google account, under your control. The developer cannot access these backups.

## Network requests

To show market and network data, the app requests public data directly from these services. The requests contain no personal information, account identifiers or your stack amount; like any internet connection, each service can see your IP address and basic request details (such as the app's user agent). Their own privacy policies apply.

| Service | Used for | When |
|---|---|---|
| api.coinbase.com | BTC price and USD/THB exchange rate | Always |
| api.exchange.coinbase.com | 24-hour change and hourly price history | Always |
| api.kraken.com | BTC price | Only if you choose Kraken as the price source |
| api.bitkub.com | BTC/THB price and change | Only if you choose Bitkub for THB |
| mempool.space | Block height, fees, difficulty, hash rate, Lightning capacity | Always |
| blockchain.info | Bitcoin in circulation | Always |
| query1.finance.yahoo.com | Strategy (MSTR) share price | Always |
| api.coingecko.com | Strategy (MSTR) bitcoin holdings | Always |

Requests run when you open or refresh the app and in the background at the interval you choose (15, 30 or 60 minutes) while a widget is placed or an alert is on.

## Permissions

- **Internet** — to fetch the data above.
- **Notifications** (Android 13 and later) — to show the alerts you switch on. Asked for only when you turn on an alert; you can revoke it in system settings at any time.
- The background-work library the app uses (Android WorkManager) also declares permissions to check network status, keep scheduled updates after a restart, and run briefly in the background. They are used only to refresh the data above.

## What the app does not do

- No sign-in or accounts.
- No advertising or advertising identifiers.
- No analytics, crash-reporting or tracking SDKs.
- No selling or sharing of personal data — the app does not collect any.
- No access to your contacts, location, camera, microphone, files or other apps.

## Children

The app is not directed at children under 13 and does not knowingly collect information from anyone.

## Not financial advice

Figures are provided as-is from third-party sources, may be delayed or inaccurate, and are not financial advice.

## Changes

If this policy changes, the updated version will be published at the same address with a new effective date.

## Contact

Questions or requests: open an issue at <https://github.com/phirapath21/btcwidget/issues>.
