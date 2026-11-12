#!/usr/bin/env bash
# Times how fast Block opens on the CI emulator (the release build, signed with the debug key),
# and how smooth its first frames are. Writes a table to the GitHub job summary when there is one.
#
#   cold start, no profile:   fresh install without any ahead-of-time compiled code
#   cold start, with profile: after the baseline profiles (the libraries' and the app's) are
#                             compiled in, as Google Play and Android do within a day or so
#
# Emulator numbers are noisy and slower than a phone: compare runs with each other, not with
# a real device. It never fails the build over a number.
set -u
PKG=io.github.phirapath21.btcwidget
# The launcher activity as the phone resolves it (falls back to the known name).
ACT=$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$PKG" 2>/dev/null | tr -d '\r' | tail -1)
case "$ACT" in */*) ;; *) ACT="$PKG/com.example.btcwidget.MainActivity" ;; esac
RUNS=${RUNS:-10}
OUT=${OUT:-app/build/reports/startup}
mkdir -p "$OUT"
# After the UI tests the emulator's screen may be off or locked: then Android starts the app
# but never draws it, and reports no time (LaunchState UNKNOWN). Keep the screen on and unlocked.
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell input keyevent 82 >/dev/null 2>&1 || true
sleep 2
LOG="$OUT/raw.log"
: > "$LOG"
echo "activity: $ACT" >> "$LOG"
adb shell pm list packages "$PKG" >> "$LOG" 2>&1

cold_starts() {
  local label=$1 times=()
  for i in $(seq 1 "$RUNS"); do
    adb shell am force-stop "$PKG"
    adb shell 'echo 3 > /proc/sys/vm/drop_caches' >/dev/null 2>&1 || true
    sleep 1
    local t
    local raw
    adb logcat -c >/dev/null 2>&1 || true
    raw=$(adb shell am start -W -n "$ACT" 2>&1 | tr -d '\r')
    echo "[$label #$i] $raw" >> "$LOG"
    t=$(echo "$raw" | awk -F': *' '/TotalTime/ {print $2}' | tr -dc '0-9')
    # Fallback: the "Displayed ... +1s234ms" line Android logs when the first frame is drawn.
    if [ -z "$t" ]; then
      sleep 3
      local shown
      shown=$(adb logcat -d -s ActivityTaskManager:I 2>/dev/null | tr -d '\r' | grep -m1 "Displayed $PKG" | grep -o '+[0-9s]*ms' | tr -d '+')
      echo "  displayed: $shown" >> "$LOG"
      if [ -n "$shown" ]; then
        local sec=0 ms
        case "$shown" in *[0-9]s[0-9]*) sec=${shown%%s*}; ms=${shown#*s} ;; *) ms=$shown ;; esac
        ms=${ms%ms}
        t=$((10#$sec * 1000 + 10#$ms))
      fi
    fi
    [ -n "$t" ] && times+=("$t")
    sleep 2
  done
  adb shell am force-stop "$PKG"
  if [ ${#times[@]} -eq 0 ]; then
    echo "| $label | no measurement (see raw.log) | | 0 |"
    return
  fi
  printf '%s\n' "${times[@]}" > "$OUT/$label.txt"
  local sorted median min max
  sorted=$(printf '%s\n' "${times[@]}" | sort -n)
  median=$(echo "$sorted" | awk '{a[NR]=$1} END {print (NR%2 ? a[(NR+1)/2] : int((a[NR/2]+a[NR/2+1])/2))}')
  min=$(echo "$sorted" | head -1); max=$(echo "$sorted" | tail -1)
  echo "| $label | $median ms | $min–$max ms | ${#times[@]} |"
}

# Frames of the first launch: how many were janky, and the slow percentiles.
first_frames() {
  adb shell am force-stop "$PKG"
  sleep 1
  adb shell am start -W -n "$ACT" >/dev/null
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
  sleep 5
  adb shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile' > "$OUT/frames.txt"
  adb shell am force-stop "$PKG"
}

{
  echo "### Startup (emulator, release build)"
  echo
  echo "| | median | range | runs |"
  echo "|---|---|---|---|"
  # No ahead-of-time code: as a sideloaded install with no profile.
  adb shell cmd package compile -m verify -f "$PKG" >/dev/null
  cold_starts "cold start, no profile"
  # Let ProfileInstaller write the profiles it ships, then compile them in.
  adb shell am start -W -n "$ACT" >/dev/null; sleep 8
  adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE "$PKG/androidx.profileinstaller.ProfileInstallReceiver" >/dev/null 2>&1 || true
  sleep 2
  adb shell am force-stop "$PKG"
  adb shell cmd package compile -m speed-profile -f "$PKG" >/dev/null
  cold_starts "cold start, with profile"
  echo
  first_frames
  echo "Frames after opening (5 s, with profile):"
  echo
  echo '```'
  cat "$OUT/frames.txt"
  echo '```'
  echo
  echo "First launch, raw (for debugging):"
  echo
  echo '```'
  head -8 "$LOG"
  echo '```'
} | tee "$OUT/summary.md"

[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$OUT/summary.md" >> "$GITHUB_STEP_SUMMARY"
exit 0
