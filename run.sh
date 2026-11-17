#!/usr/bin/env bash
# Paleidžia simuliaciją. Parinktys perduodamos programai, pvz.: ./run.sh --seed 7 --difficulty 4
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -cp out/classes bc.App "$@"
