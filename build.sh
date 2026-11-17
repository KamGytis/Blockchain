#!/usr/bin/env bash
# Sukompiliuoja programą (out/classes) ir testus (out/test). Reikia JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out/classes out/test
javac -encoding UTF-8 -d out/classes $(find src -name '*.java')
javac -encoding UTF-8 -cp out/classes -d out/test $(find test -name '*.java')
