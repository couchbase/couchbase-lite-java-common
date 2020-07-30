#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

function usage() {
   echo "usage: fetch_litecore.sh -n <url> -e <EE|CE> [-p <platform>] [-o <dir>]"
   echo "  -n|--nexus-repo <VAL>   The URL of the nexus repo containing LiteCore"
   echo "  -e|--edition <VAL>      LiteCore edition: CE or EE."
   echo "  -p|--platform <VAL>     Core platform: macos, centos6 or linux. Default inferred from current OS" 
   echo "  -o|--output <VAL>       Download target directory. Default is <root>/common/lite-core"
   echo
}

DEBUG_SUFFIX=""

shopt -s nocasematch
while [[ $# -gt 0 ]]; do
   key="$1"
   case $key in 
      -n|--nexus-repo)
         NEXUS_REPO="$2"
         shift
         shift
         ;;
      -e|--edition)
         EDITION="$2"
         shift
         shift
         ;;
      -p|--platform)
         PLATFORM="$2"
         shift
         shift
         ;;
      -o|--output)
         OUTPUT="$2"
         shift
         shift
         ;;
      -d|--debug)
         DEBUG_SUFFIX="-debug"
         shift
         ;;
      *)
         echo >&2 "Unrecognized option $key, aborting..."
         usage
         exit 1
         ;;
   esac
done

if [ -z "${NEXUS_REPO}" ]; then
   echo >&2 "Missing --nexus-repo option, aborting..."
   usage
   exit 1
fi

if [ -z "${EDITION}" ]; then
   echo >&2 "Missing --edition option, aborting..."
   usage
   exit 1
fi

if [ -z "${PLATFORM}" ]; then
   PLATFORM="${OSTYPE}"
fi
case "${PLATFORM}" in
   centos6)
      OS="centos6"
      hash tar 2>/dev/null || { echo >&2 "Unable to locate tar. Aborting..."; exit 1; }
      ;;
   centos7|linux*)
      OS="linux"
      hash tar 2>/dev/null || { echo >&2 "Unable to locate tar. Aborting..."; exit 1; }
      ;;
   darwin*)
      OS=macosx
      hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip. Aborting..."; exit 1; }
      ;;
   *)
      echo "Unsupported platform: ${PLATFORM}. Aborting..."
      exit 1
esac

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }

if [ -z "${OUTPUT}" ]; then
   OUTPUT_DIR="${SCRIPT_DIR}/../lite-core"
else
   OUTPUT_DIR="${OUTPUT}"
fi
mkdir -p "${OUTPUT_DIR}"
pushd "${OUTPUT_DIR}" > /dev/null

"${SCRIPT_DIR}/litecore_sha.sh" -v -e ${EDITION} -o .core-sha
SHA=`cat .core-sha`

LIB="litecore-${OS}-${SHA}${DEBUG_SUFFIX}"
CORE_URL="${NEXUS_REPO}/couchbase-litecore-${OS}/${SHA}/couchbase-${LIB}"
echo "Fetching LiteCore-${EDITION} from: ${CORE_URL}"

case "${OS}" in
   macosx)
      curl -Lf "${CORE_URL}.zip" -o "${LIB}.zip"
      unzip "${LIB}.zip"

      LIBLITECORE_DIR=macos/x86_64
      mkdir -p "${LIBLITECORE_DIR}"
      rm -rf "${LIBLITECORE_DIR}/"*
      mv -f lib/libLiteCore.dylib "${LIBLITECORE_DIR}"

      rm -f "${LIB}.zip"
      ;;
   centos6|linux)
      curl -Lf "${CORE_URL}.tar.gz" -o "${LIB}.tar.gz"
      tar xf "${LIB}.tar.gz"

      LIBLITECORE_DIR=linux/x86_64
      mkdir -p "${LIBLITECORE_DIR}"
      rm -rf "${LIBLITECORE_DIR}/"*
      mv -f lib/libLiteCore.so "${LIBLITECORE_DIR}"

      SUPPORT_DIR=support/linux/x86_64
      mkdir -p "${SUPPORT_DIR}" && rm -rf "${SUPPORT_DIR}/"*
      mv -f lib/libgcc*.* "${SUPPORT_DIR}"
      mv -f lib/libicu*.* "${SUPPORT_DIR}"
      mv -f lib/libstdc*.* "${SUPPORT_DIR}"
      mv -f lib/libz*.* "${SUPPORT_DIR}"
      rm -f "${SUPPORT_DIR}/"libicutest*.*

      rm -f "${LIB}.tar.gz"
      ;;
esac

rm -rf lib
popd > /dev/null

echo "Fetch $LIB Complete"

