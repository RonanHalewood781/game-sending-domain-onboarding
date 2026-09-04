#!/usr/bin/env sh
set -eu

: "${INFRAI_API_KEY:?INFRAI_API_KEY is required}"
: "${1:?usage: scripts/run-domain-onboarding.sh <sending-domain>}"

build_dir="${TMPDIR:-/tmp}/game-domain-mail-classes"
mkdir -p "$build_dir"
find src/main/java -name '*.java' -print | xargs javac -d "$build_dir"
java -cp "$build_dir" dev.gameops.domainmail.DomainOnboardingCommand "$1"
