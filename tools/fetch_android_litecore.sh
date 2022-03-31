#!/bin/bash

LATESTBUILDS_CORE="http://latestbuilds.service.couchbase.com/builds/latestbuilds/couchbase-lite-core/sha"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OUTPUT_DIR="${SCRIPT_DIR}/../lite-core"
DEBUG_SUFFIX=""

function usage() {
   echo "usage: $0 -e EE|CE [-d] [-o <dir>]"
   echo "  -e|--edition      LiteCore edition: CE or EE."
   echo "  -d|--debug        Fetch a debug version"
   echo "  -o|--output       Download target directory. Default is <root>/common/lite-core"
   echo
   exit 1
}

shopt -s nocasematch
while [[ $# -gt 0 ]]; do
   key="$1"
   case $key in 
      -e|--edition)
         EDITION="$2"
         shift
         shift
         ;;
      -d|--debug)
         DEBUG_SUFFIX="-debug"
         shift
         ;;
      -o|--output)
         OUTPUT_DIR="$2"
         shift
         shift
         ;;
      *)
         echo >&2 "Unrecognized option $key, aborting..."
         usage
         ;;
   esac
done

if [ "${EDITION}" != "CE" -a "${EDITION}" != "EE" ]; then
   echo >&2 "Unrecognized edition option '${EDITION}'. Aborting..."
   usage
fi

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }

mkdir -p "${OUTPUT_DIR}"
pushd "${OUTPUT_DIR}" > /dev/null

ARTIFACT_ID=`"${SCRIPT_DIR}/litecore_sha.sh" -e ${EDITION}`
ARTIFACTS_URL="${LATESTBUILDS_CORE}/${ARTIFACT_ID:0:2}/${ARTIFACT_ID}/couchbase-lite-core-android-"
for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    rm -f litecore.zip
    rm -rf lib

    ARTIFACT_URL="${ARTIFACTS_URL}${ABI}${DEBUG_SUFFIX}.zip"
    echo "=== Fetching Android LiteCore-${EDITION} for ${ABI}"
    echo "  from: ${ARTIFACT_URL}"

    curl -Lf "${ARTIFACT_URL}" -o litecore.zip
    unzip -o litecore.zip

    LIBLITECORE_DIR="android/${ABI}"
    rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>&1
    mkdir -p "${LIBLITECORE_DIR}"
    mv -f lib "${LIBLITECORE_DIR}"
done

rm -f litecore.zip
rm -rf lib

echo "=== Fetch complete"
find *
popd > /dev/null

