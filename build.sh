#!/usr/bin/env bash
# Compiles the project with plain javac. No Maven required.
# Usage: ./build.sh
set -euo pipefail

cd "$(dirname "$0")"
rm -rf out
mkdir -p out

find src/main/java -name "*.java" > .sources.txt
javac -d out @.sources.txt
rm .sources.txt

echo "Build complete. Classes are in ./out"
echo "Run one of:"
echo "  ./run-single.sh --in sample.pcap --out filtered.pcap"
echo "  ./run-multithreaded.sh --in sample.pcap --out filtered.pcap"
echo "  ./run-console.sh"

