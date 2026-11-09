# Releasing Blockface to Google Play

## 1. Create the upload key (once)

Keep the keystore and passwords somewhere safe and **never commit them** (`.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`).

```bash
keytool -genkeypair -v -keystore release.jks -alias blockface -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` in the project root:

```properties
storeFile=release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=blockface
keyPassword=YOUR_KEY_PASSWORD
```

With **Play App Signing** (the default for new apps), this is your *upload* key; Google holds the app signing key. If the upload key is lost, it can be reset through Play Console support.

## 2. Set the version

In `app/build.gradle.kts`, raise `versionCode` by 1 for every upload and set `versionName` (e.g. `1.0.0`, `1.0.1`).

## 3. Test the release build

R8 shrinking is on for release builds, so test the release build itself, not just debug:

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleRelease
```

Install `app/build/outputs/apk/release/app-release.apk` on a phone and check:

- [ ] Dashboard: hero panel, chips, price chart (drag on it), pull to refresh
- [ ] Widgets tab: switches, drag to reorder, collapse sections, Reset
- [ ] Add each widget (Bitcoin metrics, Price chart, Halving, Quotes); resize them; tap them
- [ ] Settings: currency USD/THB, price sources (Kraken, Bitkub), your stack, privacy policy link
- [ ] Alerts: switch one on (notification permission prompt), and wait for a background refresh
- [ ] Airplane mode: cached data shows, "offline" banner, widgets keep their last values

## 4. Build the bundle Play needs

```bash
./gradlew bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab`.

## 5. Play Console checklist

1. Create the app: name **Blockface: Bitcoin Widgets**, default language English, App, Free.
2. **Store listing:** text and graphics from `docs/play-store/listing.md` and `docs/play-store/graphics/`.
3. **App content:** privacy policy URL, Data safety, ads, app access, content rating, target audience, financial features — answers in `docs/play-store/policy-forms.md`.
4. **Testing:** start with **Internal testing**, install from the Play link and run the checklist above again. New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before production access is granted.
5. Review the **pre-launch report** (Play's automatic device tests) for crashes.
6. Promote to **Production**.

## Notes

- Application ID: `io.github.phirapath21.btcwidget` — permanent once published.
- Min SDK 24 (Android 7.0), target SDK 36.
- The app is English-only for now; Thai can be added later as a translation.
