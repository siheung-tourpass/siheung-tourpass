#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 tests/python/architecture_check.py
mkdir -p target/checks
classpath='target/classes:target/checks:target/siheung-tourpass/WEB-INF/lib/*'
checks='BoundaryCheck PlaceImagesCheck EventCatalogCheck JourneyProductCheck DiningCatalogCheck GoodsCheck CardCoordinatesCheck JourneyPhotoCheck HttpContractCheck'
set --
for check in $checks; do set -- "$@" "tests/java/$check.java"; done
javac -encoding UTF-8 -cp "$classpath" -d target/checks "$@"
for check in $checks; do
    java -ea -cp "$classpath" "$check"
done

javac -encoding UTF-8 -cp "$classpath" -d target/checks tests/java/KakaoLoginCheck.java tests/java/CatalogQueryCheck.java
java -ea -cp "$classpath" kr.ac.siheung.tourpass.service.KakaoLoginCheck
