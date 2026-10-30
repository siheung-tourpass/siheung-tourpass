# API 사용 안내

서버 실행·시연 계정은 [README](../README.md#시작하기), HTTP 계약은 [공통 요구사항](../spec.md#restful-api)을 따른다. 아래 경로는 서비스 기본 주소 `http://localhost:8080/siheung-tourpass`에 상대적이다.

## 인증과 요청

`GET /api/v1/`에서 시작해 응답의 `_links`에 있는 `href`·`method`를 따른다. API는 쿠키 대신 매 요청의 `Authorization: Basic <아이디:암호의 Base64>`로 인증한다. 변경 요청에는 `X-CSRF-Token: api`, JSON 본문에는 `Content-Type: application/json`을 전달한다. 브라우저의 자동 인증에도 변경 요청의 명시적 헤더를 요구하며 교차 출처 요청을 허용하지 않는다. Basic 인증은 로컬 시연 외 접속에서 HTTPS로 전송한다. 화면 로그인은 API 인증과 별개다.

```sh
curl --user 'visitor:DemoPass!2026' http://localhost:8080/siheung-tourpass/api/v1/
```

혜택 확인 생성 응답은 `201`이며 `Location`에서 쿠키 없이 재조회할 수 있다. 순서 상대 이동은 `POST .../move`다.

## Bruno

Bruno에서 **Open Collection**으로 [backend/bruno](../backend/bruno)를 열고 `Local` 환경을 선택한다. 서버 주소가 다르면 환경의 `baseUrl`을 변경한다. `Auth / Index` → 원하는 `Select` 요청으로 클라이언트의 인증 계정을 선택한다. 컬렉션 스크립트가 요청마다 인증·CSRF 헤더를 전달한다. [Bruno 변수 사용법](https://blog.usebruno.com/manage-variables)을 참고한다.

- 공개 조회: `Public`에서 상품·가맹점·추천 조회. `Recommendations`는 첫 추천의 `placeId`를 저장한다. 추천이 없으면 화면에서 공개 장소 ID를 확인해 환경에 입력한다.
- 여행: 방문객 계정 선택 → `Journeys / Create` → `Add place` → 필요한 수정·기록 요청. 생성 응답의 `journeyId`와 담기 응답의 `journeyItemId`를 자동 저장한다. 확정·보관 후 삭제하려면 `Restore` → `Reopen`으로 제작 중 상태로 돌아간다.
- 혜택: 방문객으로 `Passes / Issue` → 담당자 계정 선택 → `Merchant / Lookup pass` → `Redeem` → 방문객 계정 선택 → `Feedback`에서 평가. 이용권 코드·확인 토큰·사용 이력 ID는 응답에서 자동 저장한다. 기본 담당자는 `staff-a`, 가맹점 ID는 `1`이며 다른 담당자는 선택 요청의 계정과 `merchantId`를 함께 바꾼다.
- 관리자: 관리자 계정 선택 후 `Admin`의 목록·통계·이용권 상세·취소 요청을 선택한다. 자료 생성·수정·검토 요청은 이 컬렉션에 포함하지 않는다.

요청은 필요한 것만 개별 실행한다. 전체 컬렉션 실행은 역할 전환과 데이터 변경 순서를 보장하지 않는다. 발급·혜택 사용·취소·삭제는 실제 DB를 변경하므로 테스트 DB를 사용한다. 활성 이용권이 있어 발급이 거절되면 `Passes / List`에서 ID를 환경의 `passId`에 입력한 뒤 `Detail`로 코드를 저장한다. `Auth / Clear credentials`는 클라이언트에 저장한 인증 계정을 해제한다. 개인 계정 암호·코드·토큰을 환경 파일에 저장해 커밋하지 않는다.

