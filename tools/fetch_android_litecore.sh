#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OUTPUT_DIR="${SCRIPT_DIR}/../lite-core"
DEBUG_SUFFIX=""

function usage() {
   echo "usage: $0 -e EE|CE -n <url> [-d] [-o <dir>]"
   echo "  -e|--edition      LiteCore edition: CE or EE."
   echo "  -n|--nexus-repo   The URL of the nexus repo containing LiteCore"
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
      -n|--nexus-repo)
         NEXUS_REPO="$2"
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

if [ -z "${NEXUS_REPO}" ]; then
   echo >&2 "Missing nexus-repo url. Aborting..."
   usage
fi

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }

mkdir -p "${OUTPUT_DIR}"
pushd "${OUTPUT_DIR}" > /dev/null

ARTIFACT_ID=`"${SCRIPT_DIR}/litecore_sha.sh" -e ${EDITION}`

for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    ARTIFACT_URL="${NEXUS_REPO}/couchbase-litecore-android-${ABI}/${ARTIFACT_ID}/couchbase-litecore-android-${ABI}-${ARTIFACT_ID}${DEBUG_SUFFIX}"
    echo "=== Fetching Android LiteCore-${EDITION} for ${ABI}"
    echo "  from: ${ARTIFACT_URL}"

    curl -Lf "${ARTIFACT_URL}.zip" -o litecore.zip
    unzip litecore.zip

    LIBLITECORE_DIR="android/${ABI}"
    rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>&1
    mkdir -p "${LIBLITECORE_DIR}"
    mv -f lib/libLiteCore.so "${LIBLITECORE_DIR}"

    rm -f litecore.zip
    rm -rf lib
done

echo "=== Fetch complete"
find *
popd > /dev/null

