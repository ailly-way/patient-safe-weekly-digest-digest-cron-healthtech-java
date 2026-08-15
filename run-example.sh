#!/bin/sh
set -eu

build_dir="${TMPDIR:-/tmp}/patient-digest-classes"
mkdir -p "$build_dir"
javac -d "$build_dir" src/main/java/*.java
java -cp "$build_dir" DigestScheduler "$@"
