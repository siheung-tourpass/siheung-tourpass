# 구현 계획

[기술 구조](#기술-구조) · [남은 작업](#남은-작업) · [요구사항](spec.md)

## 기술 구조

전체 구성은 [README의 아키텍처 그림](README.md#기술-스택)을 따른다. 아래는 소스별 구현 책임과 의존성 기준이다.

[Maven 실행형 WAR](backend/pom.xml)로 내장/외부 Tomcat 10.1을 지원한다. JSON은 Jackson, 암호는 JDK PBKDF2다.

| 경로·원본 | 책임 |
| --- | --- |
| [화면 Controller](backend/src/main/kotlin/kr/ac/siheung/tourpass/controller/TourpassController.kt) · [API Controller](backend/src/main/kotlin/kr/ac/siheung/tourpass/controller/ApiController.kt) | 화면·API의 구체 경로 매핑; Handler 작업 선택 |
| [WebHandler](backend/src/main/java/kr/ac/siheung/tourpass/controller/WebHandler.java) · [ApiHandler](backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java) | 매핑된 작업의 폼·PRG·확인 토큰·시간 표시 / JSON 계약; 요청 트랜잭션에서 인증·CSRF 검사; API는 무상태 인증·자원 링크·HTTP 응답을 담당 |
| [SecurityFilter](backend/src/main/java/kr/ac/siheung/tourpass/filter/SecurityFilter.java) | 인증·역할·CSRF 검사 함수·캐시·보안 헤더 |
| [Service](backend/src/main/java/kr/ac/siheung/tourpass/service) | 화면/API 공통 업무 규칙·소유권·상태 전이·추천 계산·입력 검증; 데이터 접근은 DAO 호출 |
| [DAO](backend/src/main/java/kr/ac/siheung/tourpass/dao) | 기능별 조회·저장·행 잠금 SQL; Sql은 매개변수 바인딩·DB 값 변환 |
| [Database](backend/src/main/java/kr/ac/siheung/tourpass/config/Database.java) | 연결·요청 트랜잭션·쓰기 직렬화·commit/rollback |
| [SQL](backend/sql/schema.sql) · [seed](backend/sql/seed.sql) | 관계·제약 / 시연 자료·계정 |
| [frontend](frontend/WEB-INF/views) | JSP·EL/JSTL·이스케이프; SQL·업무 판정·스크립틀릿 금지 |
| [설정](backend/src/main/resources/application.properties) · [web.xml](frontend/WEB-INF/web.xml) | 내장/외부 실행·세션·JNDI |

- 업무 계층은 표현 계층에, 영속 계층은 업무·표현 계층에 의존하지 않는다. URL별 접근 정책은 SecurityFilter가 소유하고 AuthService는 계정·역할을 검증한다. Database는 공통 트랜잭션 기반이며 요청 단위 트랜잭션 안에서 인증과 업무 처리를 함께 실행한다. 계층 경계는 [아키텍처 검사](backend/tests/architecture_check.py)로 확인한다.
- 서버·SQL·데이터·스크립트·검사는 `backend/`, JSP·정적 자산은 `frontend/`, 세부 문서는 `docs/`에 둔다. 로컬 `.runtime/` 경로를 유지한다.

## 기능별 구현 경계

업무 정책은 [공통·역할별 요구사항](spec.md#역할별-요구사항), 배치·조작은 [디자인](docs/design.md)을 원본으로 관리한다. 아래 표는 수정할 구현을 찾기 위한 안내다.

| 영역 | 구현 원본 | 변경 시 유지할 경계 |
| --- | --- | --- |
| 여행 | [JourneyService](backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java) | 부모 여행 행 잠금으로 변경을 직렬화하고 카드·스냅샷·방문 기록·연결 이력을 함께 저장한다. 카드 키 해석은 `JourneyService.CardKey`로 공유하며 기존 `placeId` API와 `MERCHANT` 기록을 유지한다. |
| 템플릿·표지 | [JourneyTemplates](backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyTemplates.java) · JourneyService · WebHandler | 표지는 화면 표시 정보이며 API·저장 스냅샷에 추가하지 않는다. |
| 카드 탐색·선택 | [공통 보드](frontend/WEB-INF/views/journey-builder.jspf) · [선택 상태](frontend/assets/journey-builder.js) · [스크롤](frontend/assets/card-scroll.js) | 공개 탐색·생성·편집은 보드를 공유하며 선택 스크립트가 단계 화면·이전 이동·선택 보존을 담당한다. 여행 기록은 같은 카드 이동·키보드·포커스 처리를 사용한다. |
| 관광·추천·피드백 | [추천 모델과 구현 원본](docs/tourism-semantics.md) | 탐색·추천 승인 조건을 구분하고 의미 자료 변경·검토·버전 갱신을 트랜잭션으로 처리한다. |
| 관광지 피드백 | [PlaceFeedbackService](backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceFeedbackService.java) · [DAO](backend/src/main/java/kr/ac/siheung/tourpass/dao/PlaceFeedbackDao.java) | 방문객·관광지별 현재 평가를 저장하고 관리자에게 익명 집계만 제공한다. 추천·여행 기록·혜택 평가와 분리한다. |
| 안내 카탈로그 | [CatalogService](backend/src/main/java/kr/ac/siheung/tourpass/service/CatalogService.java) · [EventCatalog](backend/src/main/java/kr/ac/siheung/tourpass/service/EventCatalog.java) · [DiningCatalog](backend/src/main/java/kr/ac/siheung/tourpass/service/DiningCatalog.java) · [좌표 연결](backend/src/main/java/kr/ac/siheung/tourpass/service/CardCoordinates.java) | 여행·관리자·굿즈가 같은 카드 구성을 사용한다. 수집 JSON 로딩·이미지 변환은 `CatalogResources`를 공유하며 행사 필드명은 로딩 시 공통 카드 형식으로 변환한다. |
| 관리자·굿즈 | WebHandler · [GoodsService](backend/src/main/java/kr/ac/siheung/tourpass/service/GoodsService.java) · [관리자 탐색](frontend/WEB-INF/views/admin-card-browser.jspf) | 공개 대상 검증·QR 생성은 서버에서, 편집·실물 인쇄 배치는 JSP·CSS에서 처리한다. |
| 관광 사진 | [PlaceImages](backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceImages.java) | 장소 코드로 정적 사진을 연결한다. 등록·배포 기준은 [관광 데이터](docs/data-collection/tourism-data.md#이미지)를 따른다. |
| 공통 화면 | WebHandler · [JSP 조각](frontend/WEB-INF/views) | 역할별 진입을 서버에서 결정하고 사진·추천 입력·CSRF·출처 링크를 공통 조각으로 제공한다. |

API 인증은 요청별 HTTP Basic, 변경 요청은 명시적 CSRF 헤더로 보호한다. 화면 세션과 API 인증을 분리하고, 화면의 비동기 추천은 `/recommendations`의 JSON 표현을 사용한다. API의 혜택 확인 자원은 DB에 저장해 세션 없이 조회·소비한다. HTTP 계약은 [RESTful API](spec.md#restful-api)를 따른다.

여행 편집 조회는 여행·카드, 상세 조회는 혜택·연결 이력까지 포함한다. 제작 중 여행 삭제는 외래 키 CASCADE로 자식 카드·연결 이력을 정리한다. 피드백 사유·검토 초기화는 Service, 관리자 참조 선택 목록은 WebHandler가 담당한다.

## 실행 범위

로컬 PostgreSQL 16 시연을 기본으로 하며 CI만 PostgreSQL 16 컨테이너를 사용한다. 기존 DB 갱신은 [README](README.md#시작하기)의 마이그레이션 절차를 따른다. JPA·운영 Docker·RDS·로드밸런서·오토스케일링·S3·CloudFront·자동 배포는 범위에 포함하지 않는다.

## 남은 작업

| 항목 | 대기 이유 | 재개 조건·작업 |
| --- | --- | --- |
| 미확보 음식점·카페 사진 | [이미지 조사](docs/data-collection/dining-images.md)의 미확보 업소는 촬영 대상 또는 재사용 조건 미확인 | 대상 일치·이용 조건 확인 후 등록 |
| 행사 자료 | 수집 내용 관리자 검토 대기 | [행사 데이터](docs/data-collection/events-data.md)의 공식 안내를 재확인하고 검토 |
| 관광 운영 조건 | 최신 운영기관 자료 미확보 | [검토 기준](docs/data-collection/tourism-data.md#검토)에 따라 운영·예약·접근 조건을 확인 후 보완 |
| 사용자 검증 | 참여자 미확보 | [완료 기준](spec.md#완료-기준)의 참여자 확보 후 [지역 문제별 과제](docs/project-validation.md#검증-과제) 수행 |
| AWS 배포 | 공식 배포 요구 미확인 | 요구 확인 후 범위·설계 결정 |
