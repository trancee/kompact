#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 <androidNativeArm64 test.kexe>" >&2
  exit 2
fi

run_adb() {
  timeout_seconds="$1"
  shift
  python3 -c '
import subprocess
import sys

timeout_seconds = int(sys.argv[1])
try:
    result = subprocess.run(sys.argv[2:], timeout=timeout_seconds)
except subprocess.TimeoutExpired:
    print(f"ADB command timed out after {timeout_seconds}s.", file=sys.stderr)
    raise SystemExit(124)
raise SystemExit(result.returncode)
' "$timeout_seconds" adb "$@"
}

binary="$1"
description="$(file "$binary")"
if [[ "$description" != *"ELF 64-bit"* || "$description" != *"ARM aarch64"* ]]; then
  echo "Expected an Android arm64 ELF test executable; got: $description" >&2
  exit 2
fi

devices="$(run_adb 30 devices -l)"
ready_usb_devices="$(awk '$2 == "device" && $0 ~ /usb:/ { count++ } END { print count + 0 }' <<< "$devices")"
if [[ "$ready_usb_devices" != "1" ]]; then
  echo "Expected exactly one authorized USB Android device; found $ready_usb_devices." >&2
  exit 1
fi

if [[ "$(run_adb 30 -d shell -T getprop sys.boot_completed)" != "1" ]]; then
  echo "The USB Android device has not completed boot." >&2
  exit 1
fi
device_abi="$(run_adb 30 -d shell -T getprop ro.product.cpu.abi)"
if [[ "$device_abi" != "arm64-v8a" ]]; then
  echo "The USB Android device does not report arm64-v8a as its primary ABI." >&2
  exit 1
fi
device_model="$(run_adb 30 -d shell -T getprop ro.product.model)"
android_release="$(run_adb 30 -d shell -T getprop ro.build.version.release)"
android_api="$(run_adb 30 -d shell -T getprop ro.build.version.sdk)"
android_build="$(run_adb 30 -d shell -T getprop ro.build.id)"
local_sha256="$(shasum -a 256 "$binary" | awk '{ print $1 }')"

echo "ADB: $(adb version | sed -n '2p')"
echo "Target: sole authorized USB Android device; model=$device_model; release=$android_release; API=$android_api; build=$android_build; ABI=$device_abi"
echo "Test binary SHA-256: $local_sha256"

remote_dir="/data/local/tmp/kompact-android-native-tests-$$-$RANDOM"
if ! run_adb 30 -d shell -T mkdir "$remote_dir"; then
  echo "Could not reserve a unique temporary directory on the USB device." >&2
  exit 1
fi
remote_binary="$remote_dir/test.kexe"

cleanup() {
  status=$?
  trap - EXIT
  cleanup_status=0
  if ! run_adb 30 -d shell -T rm -f "$remote_binary"; then
    cleanup_status=1
  fi
  if ! run_adb 30 -d shell -T rmdir "$remote_dir"; then
    cleanup_status=1
  fi
  if [[ "$status" -ne 0 ]]; then
    exit "$status"
  fi
  if [[ "$cleanup_status" -ne 0 ]]; then
    echo "Could not remove temporary test files from the device: $remote_dir" >&2
    exit 1
  fi
  echo "Removed temporary test files from the device."
  exit 0
}
trap cleanup EXIT

run_adb 60 -d push "$binary" "$remote_binary"
run_adb 30 -d shell -T chmod 700 "$remote_binary"
remote_sha256="$(run_adb 30 -d shell -T toybox sha256sum "$remote_binary" | awk '{ print $1 }')"
if [[ "$remote_sha256" != "$local_sha256" ]]; then
  echo "The device test binary checksum does not match the downloaded artifact." >&2
  exit 1
fi
echo "Device test binary SHA-256 verified."
echo "Running Android Native tests on the sole authorized USB device."
run_adb 600 -d shell -T "$remote_binary"
