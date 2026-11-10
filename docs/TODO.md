# Blockface To-Do

Features Blockface still lacks compared with X and Amethyst (as of 10 Nov 2026).
Tick an item when its PR is merged. Size: S = small, M = medium, L = large.

**Rule:** nothing here is started without the owner's go-ahead.

## 🔴 Important (do first)

- [x] **Verify NIP-05** (S): check `name@domain` against the domain's `nostr.json` before showing ✓; today it's shown unverified, so impersonation is easy.
- [ ] **Outbox model / per-author NIP-65** (M–L): read each person's notes from the relays they write to, so notes on other relays aren't missed.
- [ ] **Link previews** (M): card with title and image for links in notes (OpenGraph).
- [ ] **Video / GIF playback** (M): play videos and animated GIFs in notes; today only still images show.
- [ ] **Upload profile picture and banner** (S): pick from the phone and upload via Blossom instead of pasting a URL.
- [ ] **In-app zaps (NWC, NIP-47)** (M–L): pay without switching to a wallet app.
- [ ] **Who liked / reposted / zapped** (S): tap a count to see the people.

## 🟡 Basics users expect

### Reading
- [ ] Global / per-relay feed, to discover people beyond follows
- [ ] Pinned note on profiles (NIP-51 kind 10001)
- [ ] Nested reply threads (today: one level)
- [ ] Long-form articles (NIP-23)
- [ ] Translate notes (e.g. into Thai)
- [ ] Polls (NIP-88)

### Writing
- [ ] Drafts (NIP-37)
- [ ] #hashtag autocomplete
- [ ] Video upload
- [ ] GIF picker (needs Tenor or GIPHY and an API key)
- [ ] Emoji reactions and custom emoji (NIP-30), beyond ♥

### Account and settings
- [ ] Multiple accounts and switching
- [ ] NIP-46 remote signer (bunker), for people not using Amber
- [ ] Export nsec, with a warning, for accounts using an nsec or created in the app
- [ ] Settings for media servers (Blossom list) and DM inbox relays
- [ ] Notification settings by type (e.g. turn off likes)
- [ ] Thai translation of the UI

### Direct messages
- [ ] Photos in DMs
- [ ] Reply to a message, and react to messages
- [ ] Read legacy DMs (NIP-04); most older Nostr DMs use it
- [ ] "Message requests" for people you don't follow (spam control)
- [ ] Create group chats from the UI (groups already work underneath)

### Safety
- [ ] Mute words, hashtags and threads (today: accounts only)
- [ ] Web-of-trust spam filter: hide accounts nobody in your network follows

## 🟢 Advanced / extras

- [ ] Communities (NIP-72), public chats (NIP-28), groups (NIP-29)
- [ ] Badges (NIP-58)
- [ ] Who to follow suggestions
- [ ] "For you" feeds via DVMs (NIP-90)
- [ ] Local event database (SQLite) instead of today's small cache
- [ ] Instant notifications (needs a push server; today it checks every 15–60 min)
- [ ] Scheduled posts

## Suggested next batch

1. Verify NIP-05
2. Upload profile picture and banner
3. Who liked / reposted / zapped
4. Link previews

Then the outbox model.
