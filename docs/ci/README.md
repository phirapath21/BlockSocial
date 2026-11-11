# CI

`android.yml` is a GitHub Actions workflow for Block. It lives here because it
could not be committed to `.github/workflows/` from the session that wrote it.

To enable it, from the repository root:

```bash
mkdir -p .github/workflows && git mv docs/ci/android.yml .github/workflows/android.yml
```

Then commit and push. It runs on pushes to `main`, on pull requests, and on demand
(Actions tab > Android CI > Run workflow):

| Job | What it checks |
|---|---|
| Unit tests & builds | JVM unit tests, Android lint (report only), debug build, release build with R8 |
| UI tests (emulator) | `app/src/androidTest` Compose tests on an API 34 emulator |

Test and lint reports and the debug APK are attached to each run as artifacts.
