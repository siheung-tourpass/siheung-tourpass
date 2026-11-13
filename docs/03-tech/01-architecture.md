# 기술 구조 및 설계 — 시흥 투어패스

[요구사항](../01-func/README.md) · [테스트 명세](../04-test/01-verification.md) · [README](../../README.md)

## 아키텍처와 경계

- 전체 구성은 [기술 스택](../../README.md#기술-스택)을 따른다. 업무 계층은 표현 계층에, 영속 계층은 상위 계층에 의존하지 않는다([아키텍처 경계 검증](../04-test/01-verification.md#아키텍처-경계-검증)).
- `SecurityFilter`가 URL 접근 정책을, `AuthService`가 계정·역할 검증을, `Database`가 요청 단위 트랜잭션을 담당한다.
- [Maven 실행형 WAR](../../backend/pom.xml)(내장/외부 Tomcat 10.1, Jackson, JDK PBKDF2)와 PostgreSQL 16([Docker 실행](../../README.md#docker-실행) · [시작하기](../../README.md#시작하기))을 사용하며 JPA·클라우드 인프라는 제외한다.

## 소스 구조

| 경로·원본 | 책임 |
| --- | --- |
| [화면 Controller](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/TourpassController.java) · [API Controller](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiController.java) | 경로 매핑 및 Handler 위임 |
| [WebHandler](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/WebHandler.java) · [ApiHandler](../../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java) | 화면 폼·PRG·세션/CSRF 처리 및 무상태 API JSON·자원 링크 응답 |
| [SecurityFilter](../../backend/src/main/java/kr/ac/siheung/tourpass/filter/SecurityFilter.java) | 인증·역할·CSRF·캐시·보안 헤더 |
| [Service](../../backend/src/main/java/kr/ac/siheung/tourpass/service) | 업무 규칙·소유권·상태 전이·추천 계산·입력 검증 |
| [DAO](../../backend/src/main/java/kr/ac/siheung/tourpass/dao) · [Database](../../backend/src/main/java/kr/ac/siheung/tourpass/config/Database.java) | SQL·행 잠금·바인딩 및 연결·요청 트랜잭션·쓰기 직렬화 |
| [SQL](../../backend/sql/schema.sql) · [seed](../../backend/sql/seed.sql) | 스키마·제약 및 시연 자료·계정 |
| [frontend](../../frontend/WEB-INF/views) · [설정](../../backend/src/main/resources/application.properties) · [web.xml](../../frontend/WEB-INF/web.xml) | JSP(`page.jsp` 진입, EL/JSTL 전용, 스크립틀릿·SQL 금지) 및 실행·세션·JNDI 설정 |

- `backend/`(서버·SQL·데이터·스크립트·`tests/{java,python,javascript,browser}`), `frontend/`(JSP·`assets/{css,js,images,places,api-docs}`), `docs/`, `.runtime/` 구조를 유지한다.

## 기능별 구현 경계

정책은 [공통·역할별 요구사항](../01-func/01-common.md#역할별-요구사항), 화면은 [디자인](../02-uxui/01-design.md)을 따른다.

| 영역 | 구현 진입점·경계 |
| --- | --- |
| 여행 | [JourneyService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java) (구성·스냅샷·방문 기록·이용권 연결, 여행 행 잠금 직렬화) |
| 투어패스 구매 | [PassService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/PassService.java) (시연 발급·가격 보존·시작 시각·기간 중복 거절·여행 연결, 방문객 행 잠금 직렬화) |
| 탐색·선택 | [공통 보드](../../frontend/WEB-INF/views/journey/journey-builder.jspf) · [선택 상태](../../frontend/assets/js/journey-builder.js) · [카드 이동](../../frontend/assets/js/card-scroll.js) · [여행 기록](../../frontend/assets/js/journey-records.js) · [하단 시트](../../frontend/assets/js/deck-sheet.js) · [지도](../../frontend/assets/js/builder-map.js) (Leaflet WebJar, OpenStreetMap 타일 CSP `img-src`) |
| 관계·추천 | [OntologyService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/OntologyService.java) (타입·순환·추론·검토) · [RecommendationService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/RecommendationService.java) ([추천 모델](02-tourism-semantics.md) 분수 정렬·이유·사용 가능 판정) |
| 평가 | [FeedbackService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/FeedbackService.java) (혜택 평가·테마 보존) · [PlaceFeedbackService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceFeedbackService.java) (관광지 평가·집계) |
| 안내 자료 | [CatalogService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/CatalogService.java) (카드·상품 혜택·추천 상품 계산) · [공통 뷰](../../frontend/WEB-INF/views/catalog/start-product.jspf) · [공통 여행 목록](../../frontend/WEB-INF/views/journey/journey-list.jspf) · [좌표](../../backend/src/main/java/kr/ac/siheung/tourpass/service/CardCoordinates.java) · [관광 사진](../../backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceImages.java) |
| 포토카드 | [GoodsService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/GoodsService.java) · [JourneyPhotoService](../../backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyPhotoService.java) · [JourneyPhotoDao](../../backend/src/main/java/kr/ac/siheung/tourpass/dao/JourneyPhotoDao.java) · [공통 포토카드 뷰](../../frontend/WEB-INF/views/shared/photocard.jspf) |

- 카카오 인증은 `KakaoLoginService`(외부 토큰·회원번호 확인 후 DB 트랜잭션 시작)와 `AuthService`·`AuthDao`가 담당한다.
- 비동기 추천은 `/recommendations` JSON을 사용하며 API 계약은 [REST API 명세](api.md#restful-api)와 [API 안내](../../README.md#api)를 따른다.

## 공통 처리 경계

- 카탈로그 목록은 혜택을 일괄 조회한 뒤 상품·가맹점별로 묶는다. 단건 조회는 같은 공개 조건과 정렬을 유지한다.
- `Sql`은 단건·목록 조회의 행 변환을 공유하며, 단건 조회는 첫 행만 읽는다.
- 여행 보드와 지도는 `BuilderMap.point`의 좌표 검증을 공유한다. 거리 계산과 표시에서는 같은 좌표를 사용한다.

## 남은 작업과 리스크

| 항목 | 대기 이유 | 재개 조건·작업 |
| --- | --- | --- |
| 미확보 음식점·카페 사진 | [이미지 조사](../data-collection/dining-images.md) 업소의 촬영 대상·재사용 조건 미확인 | 대상 일치·이용 조건 확인 후 등록 |
| 행사 자료 | 수집 내용 관리자 검토 대기 | [행사 데이터](../data-collection/events-data.md) 공식 안내 재확인·승인 |
| 관광 운영 조건 | 최신 운영기관 자료 미확보 | [검토 기준](../data-collection/tourism-data.md#검토)에 따라 운영·예약·접근 조건 보완 |
| 사용자 검증 | 참여자 미확보 | [검증 절차](../presentation/project-validation.md#검증-절차) 참여자 확보 후 수행 |
| 관리자 상품 가격·일괄 승인 | 새 상품 가격 입력(현재 0원 고정) 및 관광지 일괄 승인 미지원 | 상품 가격 입력·관광지 일괄 승인·간편 등록 추가 |
| AWS 배포 | 공식 배포 요구 미확인 | 요구 확인 후 범위·설계 결정 |
