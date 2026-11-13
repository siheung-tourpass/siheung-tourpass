# 테스트 명세 — 시흥 투어패스

[요구사항](../01-func/README.md) · [기술 구조](../03-tech/01-architecture.md) · [루트 README](../../README.md#테스트)

## 검증 전략

- CI는 PR·main 반영 시 격리된 DB에서 API·화면·관광 검사를 실행한다([README 테스트](../../README.md#테스트)).
- 정책 경계·권한·저장 결과·조작을 검증하며 단순 문구·장식·고정 건수는 고정하지 않는다. 자료 최신성 검토와 사용자 검증은 별도로 확인한다.

[카탈로그 조회 검사](../../backend/tests/java/CatalogQueryCheck.java)는 목록·상세의 혜택과 정렬, 비활성 제외, 목록 조회 횟수, JDBC 단건 변환을 검증한다. 실행 순서는 [CI](../../.github/workflows/ci.yml)를 따른다.

## 아키텍처 경계 검증

[architecture_check.py](../../backend/tests/python/architecture_check.py)로 Controller·Filter·Service의 직접 SQL/JDBC 실행, 표현 계층의 DAO 접근, 역방향 의존, JSP 내 Java·SQL 포함을 검사한다([아키텍처와 경계](../03-tech/01-architecture.md#아키텍처와-경계)). 런타임 동작은 API·동시성·화면 검사로 함께 확인한다.

## 화면 검증

데스크톱 `1920×1080`, 모바일 `360×784` CSS px 뷰포트에서 가로 넘침·메뉴 겹침·긴 라벨·폼·조작 영역을 확인한다.

## API 계약 검증

API 변경 시 [REST API 명세](../03-tech/api.md)의 정상·거부·오류·헤더 계약을 검사하고 OpenAPI와 대조한다.

## 화면 검증 기록

[모바일 핵심 유저 플로우](02-user-flows-mobile.md)와 [데스크톱 핵심 유저 플로우](03-user-flows-desktop.md)에 캡처·검증 결과를 기록한다.
