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

업무 정책은 [공통·역할별 요구사항](spec.md#역할별-요구사항), 화면 구성은 [디자인](docs/design.md)이 담당한다.

| 영역 | 구현 진입점·경계 |
| --- | --- |
| 여행 | [JourneyService](backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java)가 구성·스냅샷·방문 기록·이용권 연결을 관리한다. 여행 행 잠금으로 변경을 직렬화한다. |
| 탐색·선택 | [공통 보드](frontend/WEB-INF/views/journey-builder.jspf)와 [선택 상태](frontend/assets/journey-builder.js)를 공개 탐색·생성·편집에서 공유한다. [카드 이동](frontend/assets/card-scroll.js)은 여행 기록에서도 사용한다. |
| 추천·평가 | [추천 모델](docs/tourism-semantics.md#구현-원본)의 서비스가 추론·순위·혜택 피드백을 담당한다. [관광지 평가](backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceFeedbackService.java)는 별도로 저장·집계한다. |
| 안내 자료 | [CatalogService](backend/src/main/java/kr/ac/siheung/tourpass/service/CatalogService.java)가 여행·관리자·출력에 공통 카드 자료를 제공한다. [좌표](backend/src/main/java/kr/ac/siheung/tourpass/service/CardCoordinates.java)·[관광 사진](backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceImages.java)은 코드로 연결한다. |
| 포토카드 | [GoodsService](backend/src/main/java/kr/ac/siheung/tourpass/service/GoodsService.java)가 관리자 출력 대상 검증·QR을 담당하고 JSP·CSS가 인쇄 배치를 담당한다. 방문객 조회는 JourneyService가 소유권·상태를 검사한다. JourneyPhotoService·JourneyPhotoDao는 본인 사진 검증·비공개 DB 저장을 담당하며 공통 포토카드 뷰로 출력한다. |

화면은 세션·CSRF를, API는 요청별 Basic 인증·CSRF 헤더를 사용한다. 화면의 비동기 추천은 `/recommendations`의 JSON 표현을 사용한다. API 혜택 확인 자원은 DB에서 조회·소비한다. 요청 방법은 [API 사용 안내](docs/api-usage.md), 계약은 [RESTful API](spec.md#restful-api)를 따른다.

## 실행 범위

로컬 PostgreSQL 16 시연을 기본으로 하며 CI만 PostgreSQL 16 컨테이너를 사용한다. 기존 DB 갱신은 [README](README.md#시작하기)의 마이그레이션 절차를 따른다. JPA·운영 Docker·RDS·로드밸런서·오토스케일링·S3·CloudFront·자동 배포는 범위에 포함하지 않는다.

## 남은 작업

| 항목 | 대기 이유 | 재개 조건·작업 |
| --- | --- | --- |
| 미확보 음식점·카페 사진 | [이미지 조사](docs/data-collection/dining-images.md)의 미확보 업소는 촬영 대상 또는 재사용 조건 미확인 | 대상 일치·이용 조건 확인 후 등록 |
| 행사 자료 | 수집 내용 관리자 검토 대기 | [행사 데이터](docs/data-collection/events-data.md)의 공식 안내를 재확인하고 검토 |
| 관광 운영 조건 | 최신 운영기관 자료 미확보 | [검토 기준](docs/data-collection/tourism-data.md#검토)에 따라 운영·예약·접근 조건을 확인 후 보완 |
| 사용자 검증 | 참여자 미확보 | [검증 절차](docs/project-validation.md#검증-절차)의 참여자 확보 후 과제 수행 |
| AWS 배포 | 공식 배포 요구 미확인 | 요구 확인 후 범위·설계 결정 |
