#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 tests/architecture_check.py
mkdir -p target/checks
classpath='target/classes:target/checks:target/siheung-tourpass/WEB-INF/lib/*'
checks='BoundaryCheck PlaceImagesCheck EventCatalogCheck JourneyTemplatesCheck DiningCatalogCheck'
set --
for check in $checks; do set -- "$@" "tests/$check.java"; done
javac -encoding UTF-8 -cp "$classpath" -d target/checks "$@"
for check in $checks; do
    java -ea -cp "$classpath" "$check"
done
