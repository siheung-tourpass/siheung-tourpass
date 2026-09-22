#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p target/checks
classpath='target/classes:target/checks:target/siheung-tourpass/WEB-INF/lib/*'
javac -encoding UTF-8 -cp "$classpath" -d target/checks tests/BoundaryCheck.java tests/PlaceImagesCheck.java tests/EventCatalogCheck.java tests/JourneyTemplatesCheck.java
for check in BoundaryCheck PlaceImagesCheck EventCatalogCheck JourneyTemplatesCheck; do
    java -ea -cp "$classpath" "$check"
done
