#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p target/checks
javac -encoding UTF-8 -cp target/classes -d target/checks tests/BoundaryCheck.java
java -ea -cp target/classes:target/checks BoundaryCheck
