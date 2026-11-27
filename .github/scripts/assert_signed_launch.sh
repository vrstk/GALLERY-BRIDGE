#!/usr/bin/env bash
# Verify launch and brief process survival on a fresh CI emulator.
set -euo pipefail
PACKAGE="${1:?package required}"
LOG="${2:?log path required}"
{
  adb shell am start -W -n "$PACKAGE/com.gallerybridge.app.MainActivity" | tr -d '\r'
} | tee "$LOG"
grep -Eq '^Status: ok[[:space:]]*$' "$LOG"
PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
test -n "$PID"
sleep 5
AFTER="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
printf 'Process before: %s; after: %s\n' "$PID" "$AFTER" | tee -a "$LOG"
test "$PID" = "$AFTER"
