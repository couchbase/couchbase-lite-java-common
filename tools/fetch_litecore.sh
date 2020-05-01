#!/bin/bash -e

function usage() {
  echo "usage: fetch_litecore.sh -n <VAL> -v <VAL> -e <VAL> [-d]"
  echo "  -n|--nexus-repo <VAL>   The URL of the nexus repo containing LiteCore"
  echo "  -e|--edition <VAL>      LiteCore edition, CE or EE."
  echo
}

DEBUG_LIB=false
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
    *)
    echo >&2 "Unrecognized option $key, aborting..."
      usage
      exit 1
      ;;
  esac
done

if [ -z "$NEXUS_REPO" ]; then
  echo >&2 "Missing --nexus-repo option, aborting..."
  usage
  exit 1
fi

if [ -z "$EDITION" ]; then
  echo >&2 "Missing --edition option, aborting..."
  usage
  exit 1
fi

SUFFIX=""
if [ $DEBUG ]; then
  SUFFIX="-debug"
fi

if [[ $OSTYPE == linux* ]]; then
  OS=linux
  hash tar 2>/dev/null || { echo >&2 "Unable to locate tar, aborting..."; exit 1; }
elif [[ $OSTYPE == darwin* ]]; then
  OS=macosx
  hash unzip 2>/dev/null || { echo >&2 "Unable to locate unzip, aborting..."; exit 1; }
else
  echo "Unsupported OS ($OSTYPE), aborting..."
  exit 1
fi

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OUTPUT_DIR=$SCRIPT_DIR/../lite-core
mkdir -p $OUTPUT_DIR
pushd $OUTPUT_DIR > /dev/null

SHA=`$SCRIPT_DIR/litecore_sha.sh -e $EDITION`

CORE_URL="${NEXUS_REPO}/couchbase-litecore-${OS}/${SHA}/couchbase-litecore-${OS}-${SHA}${SUFFIX}"
echo "Fetching LiteCore-$EDITION from: $CORE_URL"

if [[ $OS == macosx ]]; then
  curl -Lf "${CORE_URL}.zip" -o "litecore-macosx${SUFFIX}.zip"
  unzip "litecore-macosx${SUFFIX}.zip"

  LIBLITECORE_DIR=macos/x86_64
  mkdir -p $LIBLITECORE_DIR && rm -rf $LIBLITECORE_DIR/*
  mv -f lib/libLiteCore.dylib $LIBLITECORE_DIR

  rm -f litecore-macosx${SUFFIX}.zip
fi

if [[ $OS == linux ]]; then
  curl -Lf "${CORE_URL}.tar.gz" -o "litecore-linux${SUFFIX}.tar.gz"
  tar xf "litecore-linux${SUFFIX}.tar.gz"

  LIBLITECORE_DIR=linux/x86_64
  mkdir -p $LIBLITECORE_DIR && rm -rf $LIBLITECORE_DIR/*
  mv -f lib/libLiteCore.so $LIBLITECORE_DIR

  LIBICU_DIR=support/linux/x86_64/libicu
  mkdir -p $LIBICU_DIR && rm -rf $LIBICU_DIR/*
  mv -f lib/libicu*.* $LIBICU_DIR
  rm -f $LIBICU_DIR/libicutest*.*

  rm -f "litecore-linux${SUFFIX}.tar.gz"
fi

rm -rf lib
popd > /dev/null

