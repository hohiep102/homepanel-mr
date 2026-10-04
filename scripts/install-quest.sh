#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
edition="${1:-community}"
build_type="${2:-debug}"
case "$edition" in community|store) ;; *) echo 'Edition: community hoặc store'; exit 1;; esac
case "$build_type" in debug|release) ;; *) echo 'Build type: debug hoặc release'; exit 1;; esac
apk="app/build/outputs/apk/$edition/$build_type/app-$edition-$build_type.apk"
package="vn.homepanel.mr.$edition"
[[ "$build_type" != debug ]] || package="$package.debug"
[[ -f "$apk" ]] || { echo 'Chưa có APK. Chạy ./scripts/build.sh trước.'; exit 1; }
quest_serials=()
while IFS= read -r device_serial; do
  [[ -n "$device_serial" ]] || continue
  device_model="$(adb -s "$device_serial" shell getprop ro.product.model | tr -d '\r')"
  case "$device_model" in
    *Quest*3*) quest_serials+=("$device_serial");;
  esac
done < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
[[ ${#quest_serials[@]} -eq 1 ]] || { echo 'Cần đúng một Quest 3/3S đã cho phép USB debugging. Kiểm tra cáp và hộp thoại trong kính.'; exit 1; }
adb -s "${quest_serials[0]}" install -r "$apk"
adb -s "${quest_serials[0]}" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$package/vn.homepanel.MainActivity"
