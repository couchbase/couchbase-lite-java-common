#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OUTPUT_DIR="${SCRIPT_DIR}/../lite-core"
DEBUG_SUFFIX=""


function usage() {
   echo "usage: $0 -e EE|CE -n <url> [-d] [-o <dir>] [-p <platform>]"
   echo "  -e|--edition      LiteCore edition: CE or EE."
   echo "  -n|--nexus-repo   The URL of the nexus repo containing LiteCore"
   echo "  -d|--debug        Fetch a debug version"
   echo "  -o|--output       Download target directory. Default is <root>/common/lite-core"
   echo "  -p|--platform     Core platform: darwin, windows, centos7 or linux. Default inferred from current OS" 
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
      -p|--platform)
         PLATFORM="$2"
         shift
         shift
         ;;
      *)
         echo >&2 "Unrecognized option $key, aborting..."
         usage
         exit 1
         ;;
   esac
done

if [ "${EDITION}" != "CE" -a "${EDITION}" != "EE" ]; then
   echo >&2 "Unrecognized edition option '${EDITION}'. Aborting..."
   usage
fi

if [ -z "${NEXUS_REPO}" ]; then
   echo >&2 "Missing nexus url. Aborting..."
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

SHA=`"${SCRIPT_DIR}/get_core_id.sh" -e ${EDITION}`

LIB="litecore-${OS}-${SHA}${DEBUG_SUFFIX}"
CORE_URL="${NEXUS_REPO}/couchbase-litecore-${OS}/${SHA}/couchbase-${LIB}"
echo "=== Fetching LiteCore-${EDITION}"
echo "  from: ${CORE_URL}"

case "${OS}" in
   macosx)
      curl -Lf "${CORE_URL}.zip" -o "${LIB}.zip"
      unzip "${LIB}.zip"

      LIBLITECORE_DIR=macos/x86_64
      rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>& 1
      mkdir -p "${LIBLITECORE_DIR}"
      mv -f lib/libLiteCore.dylib "${LIBLITECORE_DIR}"

      rm -f "${LIB}.zip"
      ;;
   windows-win64)
      curl -Lf "${CORE_URL}.zip" -o "${LIB}.zip"
      mkdir lib
      unzip -j "${LIB}.zip" -d lib

      LIBLITECORE_DIR=windows/x86_64
      rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>& 1
      mkdir -p "${LIBLITECORE_DIR}"
      mv -f lib/LiteCore.* "${LIBLITECORE_DIR}"

      rm -f "${LIB}.zip"
      ;;
   linux)
      curl -Lf "${CORE_URL}.tar.gz" -o "${LIB}.tar.gz"
      tar xf "${LIB}.tar.gz"

      LIBLITECORE_DIR=linux/x86_64
      rm -rf "${LIBLITECORE_DIR}" > /dev/null 2>& 1
      mkdir -p "${LIBLITECORE_DIR}"
      mv -f lib/libLiteCore.so "${LIBLITECORE_DIR}"

      SUPPORT_DIR=support/linux/x86_64
      rm -rf "${SUPPORT_DIR}" > /dev/null 2>& 1
      mkdir -p "${SUPPORT_DIR}" 

      mkdir "${SUPPORT_DIR}/libc++" 
      mv -f lib/libgcc* "${SUPPORT_DIR}/libc++"
      mv -f lib/libstdc* "${SUPPORT_DIR}/libc++"

      mkdir "${SUPPORT_DIR}/libicu" 
      mv -f lib/libicu* "${SUPPORT_DIR}/libicu"
      rm -f "${SUPPORT_DIR}/libicu/"libicutest*

      mkdir "${SUPPORT_DIR}/libz" 
      mv -f lib/libz*.so* "${SUPPORT_DIR}/libz"

      rm -f "${LIB}.tar.gz"
      ;;
esac

rm -rf lib

echo "=== Fetch complete"
find *
popd > /dev/null

