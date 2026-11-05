#!/usr/bin/env bash
# Usage: bash scripts/sign-release.sh UNSIGNED_APK OUTPUT_APK KEYSTORE PASSWORD_FILE SDK_DIR
set -euo pipefail
unsigned_apk="$1"
output_apk="$2"
keystore_path="$3"
password_path="$4"
sdk_path="$5"
"$sdk_path/build-tools/35.0.0/apksigner" sign --ks "$keystore_path" --ks-key-alias neon --ks-pass "file:$password_path" --out "$output_apk" "$unsigned_apk"
"$sdk_path/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$output_apk" > "$output_apk.verification.txt"
python3 "$(dirname "$0")/verify-certificate.py" "$output_apk.verification.txt" "$(dirname "$0")/../signing-certificate.sha256"
