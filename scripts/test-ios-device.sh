#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <iosArm64 test.kexe> <development .mobileprovision>" >&2
  echo "Set KOMPACT_IOS_DEVICE_UDID when several connected devices are in the profile." >&2
}

if [[ $# -ne 2 || ! -f "$1" || ! -f "$2" ]]; then
  usage
  exit 2
fi

binary="$1"
profile="$2"

run_timeout() {
  timeout_seconds="$1"
  shift
  python3 -c '
import subprocess
import sys

timeout_seconds = int(sys.argv[1])
try:
    result = subprocess.run(sys.argv[2:], timeout=timeout_seconds)
except subprocess.TimeoutExpired:
    print(f"Command timed out after {timeout_seconds}s: {sys.argv[2]}", file=sys.stderr)
    raise SystemExit(124)
raise SystemExit(result.returncode)
' "$timeout_seconds" "$@"
}

description="$(file "$binary")"
if [[ "$description" != *"Mach-O 64-bit executable arm64"* ]]; then
  echo "Expected an iOS arm64 Mach-O test executable; got: $description" >&2
  exit 2
fi
if ! vtool -show-build "$binary" | grep -q 'platform IOS$'; then
  echo "The test executable was not built for the iOS device platform." >&2
  exit 2
fi
minimum_os="$(vtool -show-build "$binary" | awk '$1 == "minos" { print $2; exit }')"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/kompact-ios-device-tests.XXXXXX")"

# The host stays installed: removing a developer's last app revokes on-device trust.
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT

profile_plist="$work_dir/profile.plist"
security cms -D -i "$profile" -o "$profile_plist" 2>/dev/null

# Prints: team, bundle id, expiry, then one SHA-1 per developer certificate.
profile_facts="$(python3 - "$profile_plist" <<'PY'
import datetime
import hashlib
import plistlib
import sys

with open(sys.argv[1], "rb") as handle:
    profile = plistlib.load(handle)
entitlements = profile.get("Entitlements", {})
team = profile["TeamIdentifier"][0]
application_identifier = entitlements.get("application-identifier", "")
prefix = team + "."
if not application_identifier.startswith(prefix):
    sys.exit("The profile application identifier does not belong to its team.")
bundle_id = application_identifier[len(prefix):]
if not bundle_id or "*" in bundle_id:
    sys.exit("Use an explicit-App-ID profile; wildcard profiles are not accepted.")
if entitlements.get("get-task-allow") is not True:
    sys.exit("Use a development profile (get-task-allow must be true).")
if profile.get("ProvisionsAllDevices") or not profile.get("ProvisionedDevices"):
    sys.exit("Use a development profile that lists its provisioned devices.")
expiry = profile["ExpirationDate"]
if expiry.tzinfo is None:
    expiry = expiry.replace(tzinfo=datetime.timezone.utc)
if expiry <= datetime.datetime.now(datetime.timezone.utc):
    sys.exit(f"The provisioning profile expired at {expiry.isoformat()}.")
print(team)
print(bundle_id)
print(expiry.isoformat())
for certificate in profile.get("DeveloperCertificates", []):
    print(hashlib.sha1(certificate).hexdigest().upper())
PY
)"
team_id="$(sed -n '1p' <<< "$profile_facts")"
bundle_id="$(sed -n '2p' <<< "$profile_facts")"
profile_expiry="$(sed -n '3p' <<< "$profile_facts")"
profile_certificates="$(sed -n '4,$p' <<< "$profile_facts")"

identities="$(security find-identity -v -p codesigning | awk '/^ *[0-9]+\)/ { print $2 }' | sort -u)"
signing_identity="$(comm -12 <(sort -u <<< "$profile_certificates") <(echo "$identities"))"
if [[ "$(grep -c . <<< "$signing_identity")" != "1" ]]; then
  echo "Expected exactly one valid local signing identity from the profile's certificates." >&2
  exit 1
fi

devices_json="$work_dir/devices.json"
run_timeout 60 xcrun devicectl list devices --json-output "$devices_json" >/dev/null
# Prints: udid, name, iOS version for connected devices provisioned in the profile.
eligible_devices="$(python3 - "$devices_json" "$profile_plist" "${KOMPACT_IOS_DEVICE_UDID:-}" <<'PY'
import json
import plistlib
import sys

with open(sys.argv[1]) as handle:
    devices = json.load(handle)["result"]["devices"]
with open(sys.argv[2], "rb") as handle:
    provisioned = set(plistlib.load(handle)["ProvisionedDevices"])
requested = sys.argv[3]
for device in devices:
    hardware = device.get("hardwareProperties", {})
    connection = device.get("connectionProperties", {})
    properties = device.get("deviceProperties", {})
    udid = hardware.get("udid", "")
    if (
        hardware.get("platform") == "iOS"
        and connection.get("pairingState") == "paired"
        and connection.get("tunnelState") == "connected"
        and properties.get("developerModeStatus") == "enabled"
        and udid in provisioned
        and (not requested or requested == udid)
    ):
        print("\t".join([udid, properties.get("name", ""), properties.get("osVersionNumber", "")]))
PY
)"
if [[ "$(grep -c . <<< "$eligible_devices")" != "1" ]]; then
  echo "Expected exactly one connected, developer-mode iOS device listed in the profile." >&2
  echo "Set KOMPACT_IOS_DEVICE_UDID to select one when several are eligible." >&2
  exit 1
fi
device_udid="$(cut -f1 <<< "$eligible_devices")"
device_name="$(cut -f2 <<< "$eligible_devices")"
device_os="$(cut -f3 <<< "$eligible_devices")"
local_sha256="$(shasum -a 256 "$binary" | awk '{ print $1 }')"

echo "Xcode: $(xcodebuild -version | tr '\n' ' ')"
echo "Target: model=$device_name; iOS=$device_os"
echo "Profile: team=$team_id; bundle=$bundle_id; expires=$profile_expiry"
echo "Test binary SHA-256: $local_sha256; minimum iOS=$minimum_os"

app="$work_dir/KompactNativeTests.app"
mkdir "$app"
cp "$binary" "$app/KompactNativeTests"
chmod 755 "$app/KompactNativeTests"
cp "$profile" "$app/embedded.mobileprovision"
python3 - "$app/Info.plist" "$work_dir/entitlements.plist" "$bundle_id" "$team_id" "$minimum_os" <<'PY'
import plistlib
import sys

info_path, entitlements_path, bundle_id, team_id, minimum_os = sys.argv[1:]
info = {
    "CFBundleDevelopmentRegion": "en",
    "CFBundleExecutable": "KompactNativeTests",
    "CFBundleIdentifier": bundle_id,
    "CFBundleInfoDictionaryVersion": "6.0",
    "CFBundleName": "KompactNativeTests",
    "CFBundlePackageType": "APPL",
    "CFBundleShortVersionString": "1.0",
    "CFBundleSupportedPlatforms": ["iPhoneOS"],
    "CFBundleVersion": "1",
    "DTPlatformName": "iphoneos",
    "LSRequiresIPhoneOS": True,
    "MinimumOSVersion": minimum_os,
    "UIDeviceFamily": [1, 2],
    "UILaunchScreen": {},
    "UIRequiredDeviceCapabilities": ["arm64"],
}
entitlements = {
    "application-identifier": f"{team_id}.{bundle_id}",
    "com.apple.developer.team-identifier": team_id,
    "get-task-allow": True,
}
with open(info_path, "wb") as handle:
    plistlib.dump(info, handle)
with open(entitlements_path, "wb") as handle:
    plistlib.dump(entitlements, handle)
PY

codesign --force --sign "$signing_identity" --entitlements "$work_dir/entitlements.plist" \
  --generate-entitlement-der --timestamp=none "$app"
codesign --verify --strict --verbose=1 "$app"

run_timeout 180 xcrun devicectl device install app --device "$device_udid" "$app" >/dev/null
echo "Installed the signed test host. Running Kotlin/Native tests on the device."

log="$work_dir/console.log"
set +e
run_timeout 900 xcrun devicectl device process launch --device "$device_udid" \
  --terminate-existing --console "$bundle_id" 2>&1 | tee "$log"
launch_status=${PIPESTATUS[0]}
set -e
if [[ "$launch_status" -ne 0 ]]; then
  echo "The device test run failed with status $launch_status." >&2
  if grep -q 'explicitly trusted by the user' "$log"; then
    echo "Trust the developer in Settings > General > VPN & Device Management, then rerun." >&2
  fi
  exit 1
fi
if grep -qE '^\[  FAILED  \]' "$log" || ! grep -qE '^\[  PASSED  \] [1-9][0-9]* tests?\.' "$log"; then
  echo "The device test run did not report a passing Kotlin/Native test summary." >&2
  exit 1
fi
