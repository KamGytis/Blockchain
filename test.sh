#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -cp out/classes:out/test bc.Tests
