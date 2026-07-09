#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
java -cp out com.dpiengine.ConsoleApp
