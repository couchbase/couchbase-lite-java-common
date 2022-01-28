#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

rm -rf "${SCRIPT_DIR}/../lite-core" > /dev/null 2>&1
mkdir -p "${SCRIPT_DIR}/../lite-core"

