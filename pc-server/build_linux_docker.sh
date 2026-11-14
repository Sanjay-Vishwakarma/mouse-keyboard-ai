#!/usr/bin/env bash
# Builds the Linux executable inside Docker (works from Windows, macOS or Linux).
# Uses Debian 11 (glibc 2.31) so the result runs on Ubuntu 20.04+, Debian 11+, Mint 20+, Fedora 32+ ...
set -euo pipefail
cd "$(dirname "$0")/.."

docker run --rm -v "$PWD:/src" -w /src/pc-server python:3.11-bullseye bash -c '
    set -e
    apt-get update -qq && apt-get install -y -qq python3-tk tk-dev >/dev/null
    VENV=/tmp/venv bash build_linux.sh
    chown -R '"$(id -u 2>/dev/null || echo 1000)"':'"$(id -g 2>/dev/null || echo 1000)"' ../release build-linux || true
'
