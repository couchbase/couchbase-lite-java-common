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
      OS=macosx
      hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip. Aborting..."; exit 1; }
      ;;
   win*)
      OS=windows-win64
      hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip. Aborting..."; exit 1; }
      ;;
   centos7|linux*)
      OS="linux"
      hash tar 2>/dev/null || { echo >&2 "Unable to locate tar. Aborting..."; exit 1; }
      ;;
   *)
      echo "Unsupported platform: ${PLATFORM}. Aborting..."
      exit 1
esac

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }

mkdir -p "${OUTPUT_DIR}"
pushd "${OUTPUT_DIR}" > /dev/null

ARTIFACT_ID=`"${SCRIPT_DIR}/litecore_sha.sh" -e ${EDITION}`
ARTIFACT_URL="${LATESTBUILDS_CORE}/${ARTIFACT_ID:0:2}/${ARTIFACT_ID}/couchbase-lite-core-${OS}${DEBUG_SUFFIX}"

echo "=== Fetching ${OS} LiteCore-${EDITION}"
echo "  from: ${ARTIFACT_URL}"

case "${OS}" in
   macosx)
      rm -f lib
      rm -f litecore.zip

      curl -Lf "${ARTIFACT_URL}.zip" -o litecore.zip
      unzip litecore.zip

      LIBLITECORE_DIR=macos/x86_64
      rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>&1
      mkdir -p "${LIBLITECORE_DIR}"
      mv -f lib "${LIBLITECORE_DIR}"
      ;;
   windows-win64)
      rm -f lib
      rm -f litecore.zip

      curl -Lf "${ARTIFACT_URL}.zip" -o litecore.zip
      unzip -j litecore.zip -d lib

      LIBLITECORE_DIR=windows/x86_64
      rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>&1
      mkdir -p "${LIBLITECORE_DIR}"
      mv -f lib "${LIBLITECORE_DIR}"
      ;;
   linux)
      rm -f lib
      rm -f litecore.tgz

      curl -Lf "${ARTIFACT_URL}.tar.gz" -o litecore.tgz
      tar xf litecore.tgz

      LIBLITECORE_DIR=linux/x86_64
      rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>&1
      mkdir -p "${LIBLITECORE_DIR}/lib"
      mv -f lib/libLiteCore.so "${LIBLITECORE_DIR}/lib"

      SUPPORT_DIR=support/linux/x86_64
      rm -rf "${SUPPORT_DIR}" > /dev/null 2>&1
      mkdir -p "${SUPPORT_DIR}" 

      mkdir "${SUPPORT_DIR}/libc++" 
      mv -f lib/libgcc* "${SUPPORT_DIR}/libc++"
      mv -f lib/libstdc* "${SUPPORT_DIR}/libc++"

      mkdir "${SUPPORT_DIR}/libicu" 
      mv -f lib/libicu* "${SUPPORT_DIR}/libicu"
      rm -f "${SUPPORT_DIR}/libicu/"libicutest*

      mkdir "${SUPPORT_DIR}/libz" 
      mv -f lib/libz*.so* "${SUPPORT_DIR}/libz"
      ;;
esac

rm -rf lib > /dev/null 2>&1
rm -f litecore.zip > /dev/null 2>&1
rm -f litecore.tgz > /dev/null 2>&1

echo "=== Fetch complete"
find *
popd > /dev/null

