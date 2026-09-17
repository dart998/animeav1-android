#!/usr/bin/env bash
set -euo pipefail

apk="AnimeAV1-v$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle | head -n1).apk"
adb install -r "$apk"
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell am force-stop com.ovelayos.animeav1
adb shell am start -n com.ovelayos.animeav1/.MainActivity

# The emulator can show Android's one-time immersive-mode tutorial over the app.
# The UI polling loop dismisses it before checking the app's own UI.

dump_ui() {
  adb shell uiautomator dump /sdcard/animeav1-ui.xml >/dev/null 2>&1 || return 1
  adb exec-out cat /sdcard/animeav1-ui.xml > /tmp/animeav1-ui.xml
  python3 scripts/ui-node.py contains /tmp/animeav1-ui.xml '' >/dev/null 2>&1
}

wait_for() {
  local phrase="$1"
  for attempt in $(seq 1 12); do
    if ! dump_ui; then sleep 2; continue; fi
    if python3 scripts/ui-node.py contains /tmp/animeav1-ui.xml 'Got it'; then
      python3 scripts/ui-node.py tap /tmp/animeav1-ui.xml 'Got it' | adb shell sh
      sleep 2
      continue
    fi
    if python3 scripts/ui-node.py contains /tmp/animeav1-ui.xml "$phrase"; then return 0; fi
    sleep 2
  done
  cat /tmp/animeav1-ui.xml
  adb logcat -d -s chromium:E AndroidRuntime:E WebViewFactory:E | tail -n 100 || true
  echo "UI missing: $phrase" >&2
  exit 1
}

wait_for 'Estás sin conexión'
python3 scripts/ui-node.py tap-id /tmp/animeav1-ui.xml ':id/navDownloads' | adb shell sh
wait_for 'Tus episodios, disponibles también sin conexión.'
adb shell input keyevent KEYCODE_BACK
wait_for 'Estás sin conexión'
echo 'Offline launch, integrated downloads and Back: OK'
