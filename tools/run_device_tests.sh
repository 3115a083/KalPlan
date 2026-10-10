#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/device-validation
trap 'adb pull /sdcard/Android/data/cc.stkmn.kalplan/files/screenshots app/build/screenshots || true' EXIT
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm clear cc.stkmn.kalplan
adb shell am instrument -w -r -e class cc.stkmn.kalplan.AppSmokeTest cc.stkmn.kalplan.test/androidx.test.runner.AndroidJUnitRunner | tee app/build/device-validation/instrumentation.txt
python3 tools/verify_device_run.py app/build/device-validation/instrumentation.txt
