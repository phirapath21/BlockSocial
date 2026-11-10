# Blockface Privacy Policy

**Effective date:** 10 November 2026

Blockface ("the app") is a client for Nostr, an open social network, with Bitcoin market and network data, home-screen widgets and alerts. This policy explains what the app does with information. In short: **the developer runs no servers and collects no personal data; there are no ads, analytics or tracking.** What you post on Nostr is public, and it goes to the Nostr relays you use, not to the developer.

## Information stored on your device

The app keeps the following only on your device, in its private app storage:

- **Your settings** — theme, display currency, refresh interval, data sources, which metrics the widget shows and their order, collapsed sections.
- **Your stack** — the amount of bitcoin you choose to enter, to show what it is worth. It is never sent anywhere by the app.
- **Alerts** — the price, fee and network-event alerts you set.
- **Cached market data** — the latest prices, network figures and hourly price history, so widgets and charts work offline.
- **Widget state** — the mode, range or quote each placed widget shows.
- **Your Nostr account** — your public key and, depending on how you log in:
  - **with a signer app** (such as Amber): which signer app to use. Your private key stays in that app and is never seen by Blockface.
  - **with an nsec or a new account**: your private key, encrypted with a key held in the phone's Android Keystore that cannot be exported. It is never sent anywhere, is excluded from Android backup and device transfer, and is deleted when you log out.
- **Your relay list**, if you edit it.
- **Drafts** — text you typed in the composer but didn't post (one per new post, reply or quote, up to 50), so you can pick up where you left off. Excluded from Android backup and device transfer, and deleted when you post or log out.
- **A temporary cache** of notes and profiles you have viewed, in memory only while the app runs.

Uninstalling the app or clearing its storage deletes all of this.

**Sharing:** when you tap Share on the dashboard, the app saves an image of that metric in its cache and hands it, with a line of text, to the app you choose in Android's share sheet (for example a Nostr client). Blockface sends nothing itself; what happens next is up to the app you picked. Your stack is never offered for sharing.

**Android backup:** if you have Android backup turned on, Android may include the app's storage (except the Nostr account file and drafts) in your device backup to your own Google account, under your control. The developer cannot access these backups.

## Nostr: what you publish is public

When you post, reply, quote, repost, like, follow, mute, report, zap, vote in a poll or edit your profile, the app signs an event with your key and sends it to your Nostr relays (by default relay.damus.io, nos.lol and relay.primal.net, or the list you set). These events are **public**, are signed by your public key, and may be copied by other relays and apps. Deleting a note sends a deletion request; relays and people who already have a copy may keep it. The developer does not operate these relays and cannot remove content from them.

To show your timeline, profiles, threads and notifications, the app requests public events from your relays. To show notes from people you follow, the app also asks the relays those people say they publish to (their NIP-65 lists), so those relays see your IP address and whose notes you load. Profiles, follow lists and relay lists are also requested from public indexer relays (purplepag.es, user.kindpag.es and indexer.coracle.social), so people's names and pictures load even when they publish elsewhere. Relays see your IP address and which events you ask for (for example, whose notes you load). Each relay's own policy applies.

**Cache:** to open quickly, the app keeps the last home timeline, profiles it has shown, and your own follow, mute and relay lists in its private cache on the phone. These are public Nostr events; Android may clear the cache, and logging out deletes it.

**Zaps:** when you zap, the app asks the recipient's Lightning address provider (the domain in their Lightning address, for example getalby.com) for an invoice and passes it to your Lightning wallet app. That provider sees your IP address, the amount and your signed zap request (who you zapped, and any comment, which become public in the zap receipt). Payment happens in your wallet app; Blockface never holds funds.

**Private messages (NIP-17):** direct messages are end-to-end encrypted on your phone (NIP-44) and wrapped so relays can't see who sent them, what they say, or exactly when (timestamps are randomized up to two days back). Each message is sent as one encrypted "gift wrap" per recipient, to their inbox relays (their kind-10050 list), plus one copy for you. If you choose "Set up" in Messages, the app publishes your own inbox relay list (public) so others know where to deliver. On your phone, only the still-encrypted wraps are cached; decrypted text is kept in memory only. With a signer app, it decrypts and encrypts messages for Blockface.

**Notifications while the app is closed:** if you're logged in and "Phone notifications" is on (Settings › Nostr), the background refresh (every 15, 30 or 60 minutes) also asks your relays for new replies, mentions, reposts, likes, zaps and private messages addressed to you, decrypts messages on the phone, and shows a notification. Message text is hidden on the lock screen. Turn it off at any time in Settings.

**Search:** words you search for (anything that isn't an npub, note id, #hashtag or name@domain) are sent to public search relays, relay.nostr.band and search.nos.today, which see the search and your IP address. A name@domain lookup asks that domain's server.

**Photos you post:** when you attach a photo, the app first re-encodes it on your phone, which removes EXIF metadata such as GPS location, camera model and time. It then uploads the photo to a Blossom media server: by default blossom.primal.net, or the servers in your own media server list (kind 10063). The upload is signed with your key. The photo becomes public at its address, and the server's own policy applies. Only photos you pick are read; the app asks for no storage or photo permission (it uses Android's photo picker).

**Wallet (Nostr Wallet Connect):** if you connect a wallet in Settings › Nostr, the connection link is stored on your phone encrypted with the Android Keystore, is excluded from backup, and is removed when you disconnect or log out. When you zap, the app sends an encrypted payment request for that invoice to your wallet's relay, and your wallet pays it. The developer never sees the link, your balance or your payments.

**Images and videos:** pictures (including animated GIFs) in notes and profiles are loaded from the addresses they point to, which can see your IP address. Videos load only when you tap play.

**Link previews:** to show a card for a web link in a note, the app reads the start of that page (title, description and image) from the linked site, which can see your IP address. Nothing about you is sent beyond a normal web request.

**Translating notes:** when you tap Translate, the note is translated on your phone with Google's ML Kit; its text is not sent anywhere. The first time you translate from a language, the app downloads that language's model (about 30 MB) from Google's servers. ML Kit, a Google library inside the app, also sends Google basic information about how the translation feature runs (such as device type, app version and performance), never the text of notes; see Google's ML Kit terms and privacy information. Nothing is downloaded or sent to Google until you first translate.

**Polls:** your vote is a public event signed by your key, sent to your relays and to the relays the poll names (where its votes are collected). Anyone can see how you voted.

**Long-form articles:** to open an article, the app asks your relays, the author's relays and any relay named in the link.

**Verified addresses (NIP-05):** to show a ✓ next to a name@domain address, the app asks that domain whether the address belongs to the account.

**Moderation:** you can mute accounts (published as your mute list) and report notes (published as a NIP-56 report to your relays). The Community rules (in the app, and at docs/community-rules.md) say what isn't allowed.

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

The table covers market data. Nostr relays, Lightning address providers and image hosts are described above; they are chosen by you or by the people you follow.

Requests run when you open or refresh the app and in the background at the interval you choose (15, 30 or 60 minutes) while a widget is placed or an alert is on.

## Permissions

- **Internet** — to fetch the data above.
- **Storage (Android 9 and below only)** — asked only when you choose "Save image", to save that picture to Pictures/Blockface. Android 10 and later need no permission for this.
- **Notifications** (Android 13 and later) — to show the alerts you switch on. Asked for only when you turn on an alert; you can revoke it in system settings at any time.
- The background-work library the app uses (Android WorkManager) also declares permissions to check network status, keep scheduled updates after a restart, and run briefly in the background. They are used only to refresh the data above.

## What the app does not do

- No accounts on any developer server: your Nostr key is yours, and the developer cannot see, recover or reset it.
- No advertising or advertising identifiers.
- No analytics, crash-reporting or tracking SDKs. (Google's ML Kit, used only when you translate a note, reports its own usage information to Google as described above.)
- No selling or sharing of personal data — the developer does not collect any.
- No access to your contacts, location, camera, microphone, files or other apps.

## Children

The app is not directed at children under 13 and does not knowingly collect information from anyone.

## Not financial advice

Figures are provided as-is from third-party sources, may be delayed or inaccurate, and are not financial advice.

## Changes

If this policy changes, the updated version will be published at the same address with a new effective date.

## Contact

Questions or requests: open an issue at <https://github.com/phirapath21/btcwidget/issues>.
