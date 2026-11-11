# 구현 계획 — 시흥 투어패스

[요구사항](../func-specs/01-common.md)을 구현하는 소스 구조·검증 전략·남은 작업을 관리한다. 설치·실행·테스트 명령은 [README](../../README.md)를 따른다.

## 기술 구조

전체 구성은 [README의 아키텍처 그림](../../README.md#기술-스택)을 따른다. 아래는 소스별 구현 책임과 의존성 기준이다.

[Maven 실행형 WAR](../../backend/pom.xml)로 내장/외부 Tomcat 10.1을 지원한다. JSON은 Jackson, 암호는 JDK PBKDF2다.

| 경로·원본 | 책임 |
| --- | --- |
| [화면 Controller](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/TourpassController.java) · [API Controller](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiController.java) | 화면·API의 구체 경로 매핑; Handler 작업 선택 |
| [WebHandler](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/WebHandler.java) · [ApiHandler](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java) | 매핑된 작업의 폼·PRG·확인 토큰·시간 표시 / JSON 계약; 요청 트랜잭션에서 인증·CSRF 검사; API는 무상태 인증·자원 링크·HTTP 응답을 담당 |
| [SecurityFilter](../../backend/src/main/java/kr/ac/siheung/tourpass/filter/SecurityFilter.java) | 인증·역할·CSRF 검사 함수·캐시·보안 헤더 |
| [Service](../../backend/src/main/java/kr/ac/siheung/tourpass/service) | 화면/API 공통 업무 규칙·소유권·상태 전이·추천 계산·입력 검증; 데이터 접근은 DAO 호출 |
| [DAO](../../backend/src/main/java/kr/ac/siheung/tourpass/dao) | 기능별 조회·저장·행 잠금 SQL; Sql은 매개변수 바인딩·DB 값 변환 |
| [Database](../../backend/src/main/java/kr/ac/siheung/tourpass/config/Database.java) | 연결·요청 트랜잭션·쓰기 직렬화·commit/rollback |
| [SQL](../../backend/sql/schema.sql) · [seed](../../backend/sql/seed.sql) | 관계·제약 / 시연 자료·계정 |
| [frontend](../../frontend/WEB-INF/views) | JSP·EL/JSTL·이스케이프; SQL·업무 판정·스크립틀릿 금지 |
| [설정](../../backend/src/main/resources/application.properties) · [web.xml](../../frontend/WEB-INF/web.xml) | 내장/외부 실행·세션·JNDI |

- 업무 계층은 표현 계층에, 영속 계층은 업무·표현 계층에 의존하지 않는다. URL별 접근 정책은 SecurityFilter가 소유하고 AuthService는 계정·역할을 검증한다. Database는 공통 트랜잭션 기반이며 요청 단위 트랜잭션 안에서 인증과 업무 처리를 함께 실행한다. 계층 경계는 [아키텍처 검사](../../backend/tests/python/architecture_check.py)로 확인한다.
- 서버·SQL·데이터·스크립트·검사는 `backend/`, JSP·정적 자산은 `frontend/`, 세부 문서는 `docs/`에 둔다. 로컬 `.runtime/` 경로를 유지한다.
- JSP는 `views/page.jsp`를 진입점으로 두고 `site`·`admin`·`catalog`·`journey`·`pass`·`shared`로 나눈다. 정적 자산은 `assets/css`·`assets/js`·`assets/images`에 두며 관광 사진·API 문서의 기존 전용 폴더는 유지한다.
- 검사는 `backend/tests/java`·`python`·`javascript`·`browser`로 나눈다. Python 검사와 공통 HTTP 도구는 같은 폴더에 두어 직접 실행과 모듈 가져오기를 유지한다.

## 기능별 구현 경계

업무 정책은 [공통·역할별 요구사항](../func-specs/01-common.md#역할별-요구사항), 화면 구성은 [디자인](02-design.md)이 담당한다.

| 영역 | 구현 진입점·경계 |
| --- | --- |
| 여행 | [JourneyService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java)가 구성·스냅샷·방문 기록·이용권 연결을 관리한다. 여행 행 잠금으로 변경을 직렬화한다. |
| 투어패스 구매 | [PassService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/PassService.java)가 시연 결제 발급·가격 보존·여행 날짜 기준 시작·기간 중복 거절·여행 자동 연결을 한 트랜잭션에서 처리한다. 중복 판정은 방문객 행 잠금으로 직렬화한다. |
| 탐색·선택 | [공통 보드](../../frontend/WEB-INF/views/journey/journey-builder.jspf)와 [선택 상태](../../frontend/assets/js/journey-builder.js)를 공개 탐색·생성·편집에서 공유한다. [카드 이동](../../frontend/assets/js/card-scroll.js)은 덱 탐색에 사용하고, [여행 기록](../../frontend/assets/js/journey-records.js)은 별점·사진 입력 보조만 담당한다. [하단 시트](../../frontend/assets/js/deck-sheet.js)는 덱 구성에서 지도·담은 목록을 접고 펼치기만 담당한다. [지도](../../frontend/assets/js/builder-map.js)는 선택 상태 이벤트만 받아 그리며 Leaflet은 WebJar로 제공하고 OpenStreetMap 타일만 외부에서 불러온다(CSP `img-src`). |
| 관계·추론 | [OntologyService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/OntologyService.java)가 타입·순환·승인 경로·추론·검토를 담당한다. |
| 추천 | [RecommendationService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/RecommendationService.java)가 후보 제외·분수 정렬·이유·이용권 사용 가능 판정을 담당한다. 계산 기준은 [추천 모델](03-tourism-semantics.md)을 따른다. |
| 평가 | [FeedbackService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/FeedbackService.java)가 성공 이력 소유권·최초 테마 보존·혜택 평가 변경을, [PlaceFeedbackService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceFeedbackService.java)가 관광지 평가 저장·집계를 담당한다. |
| 안내 자료 | [CatalogService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/CatalogService.java)가 공통 카드 자료와 상품·가맹점의 혜택 목록을 제공한다. API 목록은 기본 자료, 화면 목록은 혜택을 덧붙인 자료를 사용한다. 상품 혜택의 장소를 중복 없이 담는 여행 시작 구성·현재 담기 가능 여부, 카드별 혜택 배지와 여행에 맞는 상품 추천(담은 카드를 가장 많이 포함하는 상품, 동률은 낮은 가격)도 계산한다. 목록·상세의 여행 시작 조작은 [공통 뷰](../../frontend/WEB-INF/views/catalog/start-product.jspf)를 공유하고 진행 중·여행 기록의 목록은 [공통 여행 목록](../../frontend/WEB-INF/views/journey/journey-list.jspf)을 공유한다. [좌표](../../backend/src/main/java/kr/ac/siheung/tourpass/service/CardCoordinates.java)·[관광 사진](../../backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceImages.java)은 코드로 연결한다. |
| 포토카드 | [GoodsService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/GoodsService.java)가 관리자 출력 대상 검증·QR을 담당하고 JSP·CSS가 인쇄 배치를 담당한다. 방문객 조회는 JourneyService가 소유권·상태를 검사한다. [JourneyPhotoService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyPhotoService.java)·[JourneyPhotoDao](../../backend/src/main/java/kr/ac/siheung/tourpass/dao/JourneyPhotoDao.java)는 본인 사진 검증·비공개 DB 저장을 담당하며 [공통 포토카드 뷰](../../frontend/WEB-INF/views/shared/photocard.jspf)로 출력한다. |

카카오 인증은 KakaoLoginService가 외부 인가 코드·회원번호 확인을, AuthService·AuthDao가 방문객 계정 조회·생성을 담당한다. WebHandler는 state·세션·콜백을 처리하며 외부 호출 후 DB 트랜잭션을 연다.

화면의 비동기 추천은 `/recommendations`의 JSON 표현을 사용하고 API의 혜택 확인 자원은 DB에서 조회·소비한다. 인증 경계·HTTP 계약은 [공통 요구사항](../func-specs/01-common.md#restful-api), 요청·명세 확인은 [README의 API 안내](../../README.md#api)를 따른다.

## 실행 범위

로컬 PostgreSQL 16 시연을 기본으로 하며 [로컬 Docker 실행](04-docker.md)과 CI는 PostgreSQL 16 컨테이너를 사용한다. 기존 DB 갱신은 [README](../../README.md#시작하기)의 마이그레이션 절차를 따른다. JPA·운영 Docker·RDS·로드밸런서·오토스케일링·S3·CloudFront·자동 배포는 범위에 포함하지 않는다.

## 검증 전략

CI는 PR과 main 반영 시 실행하며 같은 브랜치의 이전 실행은 취소한다. 서로 다른 초기 데이터가 필요한 API·화면·관광 검사는 격리된 DB에서 유지한다.

테스트는 정책 경계·권한·저장 결과·사용자 조작을 검증하며, 단순 문구·장식 스타일·데이터의 고정 개수는 고정하지 않는다.

자동 검사의 실행 환경·명령은 [README 테스트](../../README.md#테스트)를 따른다. 자료 최신성 검토와 사용자 검증은 자동 검사 통과 여부와 별도로 확인한다.

## 남은 작업

| 항목 | 대기 이유 | 재개 조건·작업 |
| --- | --- | --- |
| 미확보 음식점·카페 사진 | [이미지 조사](../data-collection/dining-images.md)의 미확보 업소는 촬영 대상 또는 재사용 조건 미확인 | 대상 일치·이용 조건 확인 후 등록 |
| 행사 자료 | 수집 내용 관리자 검토 대기 | [행사 데이터](../data-collection/events-data.md)의 공식 안내를 재확인하고 검토 |
| 관광 운영 조건 | 최신 운영기관 자료 미확보 | [검토 기준](../data-collection/tourism-data.md#검토)에 따라 운영·예약·접근 조건을 확인 후 보완 |
| 사용자 검증 | 참여자 미확보 | [검증 절차](../presentation/project-validation.md#검증-절차)의 참여자 확보 후 과제 수행 |
| 관리자 상품 가격·일괄 승인 | 현재 관리자 화면은 상품 가격을 입력받지 않아 새 상품이 판매 준비 중(0원)으로 남고, 관광지는 하나씩 승인한다 | 다음 이슈에서 상품 가격 입력·관광지 일괄 승인·간단한 등록 흐름을 추가 |
| AWS 배포 | 공식 배포 요구 미확인 | 요구 확인 후 범위·설계 결정 |
