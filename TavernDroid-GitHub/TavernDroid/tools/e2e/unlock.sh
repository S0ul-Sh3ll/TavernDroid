#!/bin/bash
# Brings TavernDroid to the front and enters the test PIN if the lock screen is showing.
ADB=~/Library/Android/sdk/platform-tools/adb
PIN=${E2E_PIN:-246802}
$ADB shell am start -n io.github.taverndroid/.MainActivity >/dev/null 2>&1
sleep 2
if $ADB shell dumpsys activity activities | grep -m1 topResumedActivity | grep -q LockActivity; then
  MAP=$("$(dirname "$0")/../ui.sh" | awk -F'\t' '$2 ~ /^[0-9]$/ {print $2" "$1}')
  for ((i=0;i<${#PIN};i++)); do
    xy=$(echo "$MAP" | awk -v d="${PIN:i:1}" '$1==d {print $2}' | tr ',' ' ')
    $ADB shell input tap $xy
  done
  sleep 2
fi
$ADB shell dumpsys activity activities | grep -m1 topResumedActivity | grep -o "[a-z.]*/[.A-Za-z]*"
