# REST API 명세 — 시흥 투어패스

[기능 명세](../01-func/README.md) · [테스트 명세](../04-test/README.md)

## RESTful API

- `/api/v1`은 자원 중심 URI와 HTTP 메서드 의미(GET·HEAD 무변경, PUT·DELETE 멱등성, 상대 순서 이동은 POST)를 따른다. HEAD는 성공·오류 모두 본문 없이 상태·헤더만 반환한다.
- 모든 API 요청은 무상태(HTTP 세션 미의존)로 인증·작업 정보를 전달하며, 혜택 사용 전 확인은 영속 자원으로 관리해 차감 시 원자적으로 소비한다.
- 상태 코드, 생성 `Location`, 405 `Allow`, `HEAD`/`OPTIONS`, 캐시 헤더, 하이퍼미디어 링크(`_links`)를 제공하며 링크 유무와 무관하게 서버에서 역할·소유권·상태·CSRF를 재검증한다.

## 진입점과 원본

- 기본 경로는 `/api/v1`([README API](../../README.md#api)), 문서는 `/api-docs`([OpenAPI 원본](../../frontend/assets/api-docs/openapi.yaml))다.
- [ApiController](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiController.java)(매핑) · [ApiHandler](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java)(응답) · [ApiLinks](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiLinks.java)(링크)가 구현 원본이다.

## 인증과 오류

- 보호 API는 HTTP Basic 인증(시연 외 HTTPS 필수)과 변경 요청 `X-CSRF-Token: api`를 요구한다.
- 오류는 `{error: {code, message, fieldErrors}}`(401은 `WWW-Authenticate: Basic` 포함) 형식이며, 경로·메서드 변경 시 OpenAPI 수정 후 [명세 검사](../../backend/tests/python/openapi_check.py)로 대조한다.
