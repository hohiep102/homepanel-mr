#!/usr/bin/env bash
# Put exactly one, freshly built HomePanel on the connected Quest before asking anyone to test.
# The Store edition is upgraded in place with its own key, so the Home Assistant login and placements stay;
# every other HomePanel edition is removed so an old build cannot be opened by mistake.
set -euo pipefail
cd "$(dirname "$0")/.."
KEEP=vn.homepanel.mr.store
ADB="$(grep '^sdk.dir=' local.properties | cut -d= -f2)/platform-tools/adb"
APK=app/build/outputs/apk/store/release/app-store-release.apk

python3 scripts/build-release.py --edition store
[[ "$("$ADB" get-state 2>/dev/null)" == device ]] || { echo 'No headset connected over adb.' >&2; exit 1; }

for package in $("$ADB" shell pm list packages | tr -d '\r' | sed -n 's/^package://p' | grep '^vn\.homepanel\.' || true); do
  [[ "$package" == "$KEEP" ]] && continue
  echo "Removing $package"; "$ADB" uninstall "$package" >/dev/null
done

"$ADB" shell am force-stop "$KEEP" || true
if ! "$ADB" install -r "$APK"; then
  echo "In-place upgrade refused (different signing key); reinstalling $KEEP, which signs out of Home Assistant." >&2
  "$ADB" uninstall "$KEEP" >/dev/null; "$ADB" install "$APK"
fi

# The headset must run the bytes just built, not an earlier install.
local_sum=$(shasum -a 256 "$APK" | cut -d' ' -f1)
device_sum=$("$ADB" shell sha256sum "$("$ADB" shell pm path "$KEEP" | tr -d '\r' | sed -n 's/^package://p' | head -1)" | cut -d' ' -f1)
[[ "$local_sum" == "$device_sum" ]] || { echo "Installed APK does not match the build ($device_sum vs $local_sum)." >&2; exit 1; }
# Unit tests run on the desktop JVM, which accepts things Android does not (regex flags, APIs). Open the app on
# the headset and fail on any crash before anyone is asked to test.
"$ADB" logcat -G 16M >/dev/null 2>&1 || true  # camera/player logs must outlive system frame-drop spam
"$ADB" logcat -c; "$ADB" logcat -b crash -c
"$ADB" shell am start -W -n "$KEEP/vn.homepanel.MainActivity" >/dev/null
for _ in 1 2 3 4 5 6 7 8; do sleep 1; "$ADB" logcat -d -b crash | grep -q "Process: $KEEP" && break; done
if "$ADB" logcat -d -b crash | grep -q "Process: $KEEP" || [[ -z "$("$ADB" shell pidof "$KEEP" | tr -d '\r')" ]]; then
  "$ADB" logcat -d -b crash | grep -A25 "Process: $KEEP" | head -40 >&2
  echo "The app crashed or exited on launch; not ready to test." >&2; exit 1
fi
"$ADB" logcat -c
echo "Ready to test: $("$ADB" shell pm list packages | grep -c homepanel) HomePanel app on the headset, $KEEP $(git rev-parse --short HEAD)$(git diff --quiet HEAD -- app || echo '+local changes'), $("$ADB" shell dumpsys package "$KEEP" | grep -m1 lastUpdateTime | tr -s ' ')"
