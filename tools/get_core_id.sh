#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
ROOT_DIR="${SCRIPT_DIR}/../.."


function usage() {
    echo "usage: -e CE|EE [-o <path>]"
    echo "  -e|--edition      LiteCore edition, CE or EE."
    echo "  -o|--output-path  optional: output file for result"
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
        -o|--out-path)
        OUTPUT_PATH="$2"
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
   exit 1
fi

CORE_ID=`grep "${EDITION}: " "${ROOT_DIR}/core_version.txt"`
CORE_ID="${CORE_ID:4}"

if [ -z "${OUTPUT_PATH}" ]; then
    echo "${CORE_ID}"
else
    echo "${CORE_ID}" > "${OUTPUT_PATH}"
fi

