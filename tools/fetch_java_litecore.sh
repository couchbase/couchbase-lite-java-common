#!/bin/bash

LATESTBUILDS_CORE="http://latestbuilds.service.couchbase.com/builds/latestbuilds/couchbase-lite-core/sha"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OUTPUT_DIR="${SCRIPT_DIR}/../lite-core"
DEBUG_SUFFIX=""

function usage() {
   echo "usage: $0 -e EE|CE [-d] [-o <dir>] [-p <platform>]"
   echo "  -p|--platform     Core platform: darwin, windows, centos7 or linux. Default inferred from current OS" 
   echo "  -e|--edition      LiteCore edition: CE or EE."
   echo "  -d|--debug        Fetch a debug version"
   echo "  -o|--output       Download target directory. Default is <root>/common/lite-core"
   echo
   exit 1
}

function fail() {
   echo "Artifact does not exist"
   popd > /dev/null
   exit 5
}

shopt -s nocasematch
while [[ $# -gt 0 ]]; do
   key="$1"
   case $key in 
      -p|--platform)
         PLATFORM="$2"
         shift
         shift
         ;;
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

if [ -z "${PLATFORM}" ]; then PLATFORM="${OSTYPE}"; fi
case "${PLATFORM}" in
   darwin*)
      hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip. Aborting..."; exit 1; }
      OS=macosx
      LIBLITECORE_DIR="${OUTPUT_DIR}/macos/x86_64"
      ;;
   win*)
      hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip. Aborting..."; exit 1; }
      OS=windows-win64
      LIBLITECORE_DIR="${OUTPUT_DIR}/windows/x86_64"
      ;;
   centos7|linux*)
      hash tar 2>/dev/null || { echo >&2 "Unable to locate tar. Aborting..."; exit 1; }
      OS="linux"
      LIBLITECORE_DIR="${OUTPUT_DIR}/linux/x86_64"
      ;;
   *)
      echo "Unsupported platform: ${PLATFORM}. Aborting..."
      exit 1
esac

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }

ARTIFACT_ID=`"${SCRIPT_DIR}/litecore_sha.sh" -e ${EDITION}`
ARTIFACT_URL="${LATESTBUILDS_CORE}/${ARTIFACT_ID:0:2}/${ARTIFACT_ID}/couchbase-lite-core-${OS}${DEBUG_SUFFIX}"

rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>&1
mkdir -p "${LIBLITECORE_DIR}" 
pushd "${LIBLITECORE_DIR}" > /dev/null

echo "=== Fetching ${OS} LiteCore-${EDITION}"
echo "  from: ${ARTIFACT_URL}"

case "${OS}" in
   macosx)
      curl -Lfs "${ARTIFACT_URL}.zip" -o litecore.zip || fail
      unzip -qq litecore.zip
      rm -rf litecore.zip
      ;;
   windows-win64)
      curl -Lfs "${ARTIFACT_URL}.zip" -o litecore.zip || fail
      unzip -qq litecore.zip -d lib
      rm -rf litecore.zip
      ;;
   linux)
      curl -Lfs "${ARTIFACT_URL}.tar.gz" -o litecore.tgz || fail
      tar xf litecore.tgz
      rm -rf litecore.tgz

      # We have to know exactly what is in the support directory
      # This is pretty fragile but it doesn't change very often.
      SUPPORT_DIR="${OUTPUT_DIR}/support/linux/x86_64"
      rm -rf "${SUPPORT_DIR}" > /dev/null 2>&1
      mkdir -p "${SUPPORT_DIR}" 

      mkdir "${SUPPORT_DIR}/libc++" > /dev/null 2>&1
      mv -f lib/libgcc* "${SUPPORT_DIR}/libc++"
      mv -f lib/libstdc* "${SUPPORT_DIR}/libc++"

      mkdir "${SUPPORT_DIR}/libicu"  > /dev/null 2>&1
      mv -f lib/libicu* "${SUPPORT_DIR}/libicu"
      rm -f "${SUPPORT_DIR}/libicu/"libicutest*
      ;;
esac

popd > /dev/null
echo "=== Fetch complete"

