# Play Console policy forms — suggested answers

These match what the app does today (see `docs/privacy-policy.md`). Re-check them if features change. Play Console wording shifts over time; when a question differs from what is below, answer from the facts in the privacy policy.

## Data safety (App content > Data safety)

With Nostr, the app sends what the user posts to Nostr relays (third parties the user picks). Google counts data an app transmits off the device as "collected", even when the developer never receives it, so declare it. Suggested answers (re-check against the current form):

- **Does your app collect or share any of the required user data types?** Yes.
- **Data types, all "Collected", not "Shared"** (sent at the user's request to services they choose; the user-initiated-transfer exemption covers "sharing"):
  - **Personal info › User IDs:** the Nostr public key. Purpose: App functionality. Optional (only with a Nostr account).
  - **Personal info › Name:** the profile name the user sets. App functionality. Optional.
  - **Messages › Other in-app messages** and **App activity › Other user-generated content:** notes, replies, reposts, likes, follows, mutes, reports, zap comments. App functionality. Optional.
  - **Messages › Other in-app messages (private):** NIP-17 direct messages, end-to-end encrypted on the device and sent to the recipients' relays at the user's request. App functionality. Optional. Encrypted in transit and at rest on relays; the developer can't read them.
  - **Photos and videos › Photos:** images the user attaches to a post, uploaded to a Blossom media server. App functionality. Optional. EXIF metadata (including location) is removed before upload.
  - **Photos and videos › Videos:** videos the user attaches to a post, uploaded as-is to a Blossom media server. App functionality. Optional. Metadata in the file is not removed (the app warns the user).
  - **App activity › Other actions** is not needed beyond the above.
  - **App activity › In-app search history:** search words are sent to NIP-50 search relays when the user searches. App functionality. Optional. Not stored by the developer.
  - **App info and performance › Diagnostics:** Google's ML Kit (used only when the user translates a note) sends Google API usage and performance information, not note text. Declare per Google's current ML Kit data disclosure (developers.google.com/ml-kit, "Data disclosure"); it is collected by the SDK, not the developer.
- **Is data processed ephemerally?** No (relays store public notes).
- **Is all of the user data collected by your app encrypted in transit?** Yes: relays use wss:// (TLS), APIs use HTTPS. (Users can add a plain ws:// relay themselves; mention this if the form asks.)
- **Can users request deletion?** The app sends NIP-09 deletion requests for the user's own notes, and logging out deletes the key from the phone. Relays are third parties; the developer can't delete data from them. Answer per the form's options and point to the privacy policy.
- Settings, the stack amount, alerts and cached market data stay on the device and are not collected. The private key (nsec) never leaves the device.

## User-generated content (Google Play UGC policy)

Apps where users post content must, in the app: let users **report** objectionable content (Report on each note → NIP-56 report), let users **block** abusive users (Mute account → NIP-51 mute list, hides them everywhere in the app), and publish **terms that forbid objectionable content**: the **Community rules** page (Settings › Nostr › Community rules, and linked at sign-up; same text in `docs/community-rules.md`).

## App access

- **All functionality is available without special access.** A Nostr account is optional and self-created in the app (no developer server, no credentials for reviewers needed). Reviewers can tap "Create account" to try posting.

## Ads

- **Does your app contain ads?** No.

## Content rating (IARC questionnaire)

- Category: **Reference, News, or Educational** — or **All Other App Types** if that fits the form better.
- Violence, sexuality, language, controlled substances, gambling: **No**.
  - Note: one quote in the quote widget contains Thai profanity ("เงินเฟ้อแม่งเหี้ย"). If the questionnaire asks about crude language or profanity, answer **Yes (mild)**, or remove that quote before submitting.
- **Users can interact / share content: Yes** (Nostr posts, replies, follows). Location sharing / digital purchases: **No**.
- Zaps pay through the user's own Lightning wallet app; Block sells nothing.

## Target audience and content

- **Target age group:** 18 and over (finance content; avoids the Families policy).
- **Appeals to children:** No.

## Financial features declaration

- Block shows market data and lets users "zap" (tip) via their own external Lightning wallet: it fetches an invoice and opens the wallet app. It does not hold funds, trade, lend, transfer money itself or act as a wallet or exchange.
- Choose the option for apps that provide **no financial features** / information only. If the form requires a cryptocurrency choice, pick the one for **price information or market data only**, not wallets or exchanges.

## Government apps, news apps, health apps

- Not applicable: **No** to each.

## Permissions declarations

- `POST_NOTIFICATIONS`: price, fee and network-event alerts the user switches on.
- No sensitive permissions (location, SMS, call log, files, accessibility, exact alarms) are used, so no extra declarations are needed.
