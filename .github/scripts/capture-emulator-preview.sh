#!/usr/bin/env bash
set -euo pipefail

preview_dir="emulator-preview"
package_name="com.yayo.sshtunneling.dev"
activity_name="com.yayo.sshtunneling.MainActivity"

mkdir -p "$preview_dir"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop "$package_name"
adb shell am start -W -n "$package_name/$activity_name"
sleep 5

adb exec-out screencap -p > "$preview_dir/01-home.png"

device_size="$(adb shell wm size | tr -d '\r' | sed -n 's/.*Physical size: //p')"
device_width="${device_size%x*}"
device_height="${device_size#*x}"
navigation_y="$((device_height - 160))"

adb shell screenrecord --time-limit 12 /sdcard/preview.mp4 &
record_pid=$!

adb shell input tap "$((device_width / 2))" "$navigation_y"
sleep 2
adb exec-out screencap -p > "$preview_dir/02-tunnels.png"

adb shell input tap "$((device_width * 5 / 6))" "$navigation_y"
sleep 2
adb exec-out screencap -p > "$preview_dir/03-settings.png"

adb shell input tap "$((device_width / 6))" "$navigation_y"
sleep 2
adb exec-out screencap -p > "$preview_dir/04-home-return.png"

adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml "$preview_dir/window.xml"

wait "$record_pid" || true
adb pull /sdcard/preview.mp4 "$preview_dir/preview.mp4" || true
adb shell dumpsys window windows > "$preview_dir/window-state.txt"
