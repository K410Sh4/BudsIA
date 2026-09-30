#!/usr/bin/env bash
set -euo pipefail

VERSION="1.13.8"
AAR_NAME="sherpa-onnx-${VERSION}.aar"
EXPECTED_SHA256="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${AAR_NAME}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_DIR="${ROOT_DIR}/.vendor-cache/sherpa-onnx/${VERSION}"
AAR_PATH="${CACHE_DIR}/${AAR_NAME}"
EXTRACT_DIR="${CACHE_DIR}/extracted"
JNI_ROOT="${ROOT_DIR}/app/src/main/jniLibs"

mkdir -p "${CACHE_DIR}"

if [[ ! -f "${AAR_PATH}" ]]; then
  curl --fail --location --retry 4 --retry-delay 2     --output "${AAR_PATH}.tmp" "${URL}"
  mv "${AAR_PATH}.tmp" "${AAR_PATH}"
fi

echo "${EXPECTED_SHA256}  ${AAR_PATH}" | sha256sum --check -

rm -rf "${EXTRACT_DIR}"
mkdir -p "${EXTRACT_DIR}"

unzip -q -o "${AAR_PATH}"   "jni/arm64-v8a/*"   "jni/x86_64/*"   -d "${EXTRACT_DIR}"

for ABI in arm64-v8a x86_64; do
  SOURCE="${EXTRACT_DIR}/jni/${ABI}"
  DEST="${JNI_ROOT}/${ABI}"
  mkdir -p "${DEST}"
  find "${DEST}" -maxdepth 1 -type f -name '*.so' -delete
  cp "${SOURCE}"/*.so "${DEST}/"

  test -f "${DEST}/libsherpa-onnx-jni.so"
  test -f "${DEST}/libonnxruntime.so"
done

echo "sherpa-onnx Android runtime ${VERSION} verified and installed."
