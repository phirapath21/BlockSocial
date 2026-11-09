# Play Console policy forms — suggested answers

These match what the app does today (see `docs/privacy-policy.md`). Re-check them if features change. Play Console wording shifts over time; when a question differs from what is below, answer from the facts in the privacy policy.

## Data safety (App content > Data safety)

- **Does your app collect or share any of the required user data types?** No.
  - The app sends no personal or device data off the device. Settings, the user's stack, alerts and cached data stay in local app storage.
  - Requests to public market-data APIs carry no user data; the IP address those services see is part of any network connection, which Google's guidance does not count as collection by the app when it is not used or stored by the developer.
- **Is all of the user data collected by your app encrypted in transit?** Not applicable (no data collected). All requests use HTTPS.
- **Do you provide a way for users to request that their data is deleted?** Not applicable; uninstalling or clearing storage removes everything.

## App access

- **All functionality is available without special access** (no login).

## Ads

- **Does your app contain ads?** No.

## Content rating (IARC questionnaire)

- Category: **Reference, News, or Educational** — or **All Other App Types** if that fits the form better.
- Violence, sexuality, language, controlled substances, gambling: **No**.
  - Note: one quote in the quote widget contains Thai profanity ("เงินเฟ้อแม่งเหี้ย"). If the questionnaire asks about crude language or profanity, answer **Yes (mild)**, or remove that quote before submitting.
- User interaction / sharing content / location sharing / digital purchases: **No**.

## Target audience and content

- **Target age group:** 18 and over (finance content; avoids the Families policy).
- **Appeals to children:** No.

## Financial features declaration

- Blockface shows market data only. It does not hold funds, trade, lend, transfer money or act as a wallet or exchange.
- Choose the option for apps that provide **no financial features** / information only. If the form requires a cryptocurrency choice, pick the one for **price information or market data only**, not wallets or exchanges.

## Government apps, news apps, health apps

- Not applicable: **No** to each.

## Permissions declarations

- `POST_NOTIFICATIONS`: price, fee and network-event alerts the user switches on.
- No sensitive permissions (location, SMS, call log, files, accessibility, exact alarms) are used, so no extra declarations are needed.
