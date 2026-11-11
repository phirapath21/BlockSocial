# Block To-Do

Features Block still lacks compared with X and Amethyst (as of 10 Nov 2026).
Tick an item when its PR is merged. Size: S = small, M = medium, L = large.

**Rule:** nothing here is started without the owner's go-ahead.

## 🔴 Important (do first)

- [x] **Verify NIP-05** (S): check `name@domain` against the domain's `nostr.json` before showing ✓; today it's shown unverified, so impersonation is easy.
- [x] **Outbox model / per-author NIP-65** (M–L): read each person's notes from the relays they write to, so notes on other relays aren't missed.
- [x] **Link previews** (M): card with title and image for links in notes (OpenGraph).
- [x] **Video / GIF playback** (M): play videos and animated GIFs in notes; today only still images show.
- [x] **Upload profile picture and banner** (S): pick from the phone and upload via Blossom instead of pasting a URL.
- [x] **In-app zaps (NWC, NIP-47)** (M–L): pay without switching to a wallet app.
- [x] **Who liked / reposted / zapped** (S): tap a count to see the people.

## 🟡 Basics users expect

### Reading
- [x] Global / per-relay feed, to discover people beyond follows
- [x] Pinned note on profiles (NIP-51 kind 10001)
- [x] Nested reply threads (today: one level)
- [x] Long-form articles (NIP-23)
- [x] ~~Translate notes (e.g. into Thai)~~ (removed Nov 2026 to keep the app small)
- [x] Polls (NIP-88)

### Writing
- [x] Drafts, saved on the phone (NIP-37 relay sync not done)
- [x] #hashtag autocomplete
- [x] Video upload
- [ ] GIF picker (needs Tenor or GIPHY and an API key)
- [x] Emoji reactions and custom emoji (NIP-30), beyond ♥

### Account and settings
- [x] Multiple accounts and switching
- [x] NIP-46 remote signer (bunker), for people not using Amber
- [x] Export nsec, with a warning, for accounts using an nsec or created in the app
- [x] Settings for media servers (Blossom list) and DM inbox relays
- [x] Notification settings by type (e.g. turn off likes)
- [ ] Thai translation of the UI

### Direct messages
- [x] Photos in DMs
- [x] Reply to a message, and react to messages
- [x] Read legacy DMs (NIP-04); most older Nostr DMs use it
- [x] "Message requests" for people you don't follow (spam control)
- [x] Create group chats from the UI (groups already work underneath)

### Safety
- [x] Mute words, hashtags and threads (today: accounts only)
- [x] Web-of-trust spam filter: hide accounts nobody in your network follows

## 🟢 Advanced / extras

- [x] Communities (NIP-72), public chats (NIP-28), groups (NIP-29)
- [x] Badges (NIP-58)
- [x] Who to follow suggestions
- [x] "For you" feeds via DVMs (NIP-90)
- [x] Local event database (SQLite) instead of today's small cache
- [x] Lists of people (NIP-51) you can read as a feed, like X's Lists
- [x] Relay status: which relays are connected, failing or slow, with retry
- [x] Grouped notifications ("Alice and 5 others liked your note")
- [x] Feeds keep their scroll position when switching tabs or coming back
- [x] Blurred image previews (BlurHash)
- [x] Private bookmarks and mutes (NIP-51 encrypted)
- [x] Picture-in-Picture video
- [x] Picture and video posts (NIP-68, NIP-71) and feeds
- [x] Live streams (NIP-53) with chat
- [x] Live streams in Picture-in-Picture (play/pause, mute) and an in-app mini player
- [x] Follow hashtags (contact list "t" tags)
- [x] Follow packs (kind 39089)
- [x] Highlights (NIP-84)
- [x] Relay management: NIP-65 read/write, search and blocked relays, NIP-11 info
- [ ] Instant notifications (needs a push server; today it checks every 15–60 min)
- [x] Scheduled posts (sent by the phone at that time)
- [x] Home feed menu like Primal: Latest, with replies, Global, one relay, #hashtag and DVM feeds; add, remove, reorder

## Suggested next batch

1. Verify NIP-05
2. Upload profile picture and banner
3. Who liked / reposted / zapped
4. Link previews

Then the outbox model.
