# 구현 계획

## 기술 구조

전체 구성은 [README의 아키텍처 그림](README.md#기술-스택)을 따른다. 아래는 소스별 구현 책임과 의존성 기준이다.

[Maven 실행형 WAR](backend/pom.xml)로 내장/외부 Tomcat 10.1을 지원한다. JSON은 Jackson, 암호는 JDK PBKDF2다.

| 경로·원본 | 책임 |
| --- | --- |
| [화면 Controller](backend/src/main/kotlin/kr/ac/siheung/tourpass/controller/TourpassController.kt) · [API Controller](backend/src/main/kotlin/kr/ac/siheung/tourpass/controller/ApiController.kt) | 화면·API의 구체 경로 매핑; Handler 작업 선택 |
| [WebHandler](backend/src/main/java/kr/ac/siheung/tourpass/controller/WebHandler.java) · [ApiHandler](backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java) | 매핑된 작업의 폼·PRG·확인 토큰·시간 표시 / JSON 계약; 요청 트랜잭션에서 인증·CSRF 검사 |
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

| 영역 | 구현 원본·책임 |
| --- | --- |
| 여행 구성 | [JourneyService](backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java)가 카드 재검증·순서·스냅샷·방문 기록·상태 전이·이용권 연결 이력을 처리한다. 부모 여행 행 잠금으로 변경을 직렬화하고 여행·선택·연결 이력을 같은 트랜잭션에 저장한다. |
| 여행 저장·조회 | `journey_place`의 카드 종류·고유 키로 항목을 구분하며 기존 `placeId` API와 `MERCHANT` 기록을 유지한다. 편집 조회는 여행·카드, 상세 조회는 혜택·연결 이력까지 포함한다. 제작 중 여행 삭제 시 자식 카드·연결 이력은 외래 키 CASCADE로 정리한다. |
| 템플릿·표지 | [JourneyTemplates](backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyTemplates.java)가 지정 장소 코드로 시작 구성·제공 가능 여부를 판정한다. 패스 표지는 JourneyService의 항목 일괄 조회와 템플릿에서 연결하고 WebHandler가 표시 정보를 구성한다. API·저장 스냅샷에는 표지를 추가하지 않는다. |
| 탐색·선택 화면 | [공통 카드 보드](frontend/WEB-INF/views/journey-builder.jspf)와 [선택 상태](frontend/assets/journey-builder.js)를 `/explore`·여행 생성·편집에서 공유한다. WebHandler는 카드 정보를, JSP는 앞뒷면을, JavaScript는 선택·전환을 담당한다. [카드 스크롤](frontend/assets/card-scroll.js)은 탐색 덱·여행 기록의 이동·키보드·포커스를 공유한다. |
| 관광·추천·피드백 | [OntologyService](backend/src/main/java/kr/ac/siheung/tourpass/service/OntologyService.java)는 탐색·추천 승인 조건을 분리하고 변경·검토·버전 갱신을 트랜잭션으로 처리한다. 계산·구현 원본은 [추천 모델](docs/tourism-semantics.md)을 따른다. |
| 안내 카탈로그 | [CatalogService](backend/src/main/java/kr/ac/siheung/tourpass/service/CatalogService.java)는 가맹점 조건과 음식점의 활성 지역 일괄 조회를 담당한다. [EventCatalog](backend/src/main/java/kr/ac/siheung/tourpass/service/EventCatalog.java)·[DiningCatalog](backend/src/main/java/kr/ac/siheung/tourpass/service/DiningCatalog.java)는 수집 JSON의 안내 카드를 제공하고 `CatalogResources`의 JSON 로더·이미지 변환을 공유한다. `/events`와 새 여행은 행사 카드 뷰를 공유한다. |
| 관광 사진 | [PlaceImages](backend/src/main/java/kr/ac/siheung/tourpass/service/PlaceImages.java)가 [메타데이터](backend/src/main/resources/place-images.json)의 장소 코드로 정적 사진을 연결하고 홈 사진을 무작위 선택한다. |
| 공통 입력·표시 | 추천 입력·폼 CSRF·사진 출처 링크는 공통 JSP 조각을 사용한다. 피드백 사유·검토 초기화는 Service에서 정의하고, 관리자 참조 선택 목록은 WebHandler가 필드명으로 전달한다. |

상태·권한은 [요구사항](spec.md), 화면 동작은 [디자인](docs/design.md), 데이터별 원본·검토 기준은 [관광](docs/tourism-data.md)·[행사](docs/events-data.md)·[음식점 데이터](docs/dining-data.md)를 따른다.

## 실행 범위

로컬 PostgreSQL 16 시연을 기본으로 하며 CI만 PostgreSQL 16 컨테이너를 사용한다. 기존 DB 갱신은 [README](README.md#시작하기)의 마이그레이션 절차를 따른다. JPA·운영 Docker·RDS·로드밸런서·오토스케일링·S3·CloudFront·자동 배포는 범위에 포함하지 않는다.

## 남은 작업

| 항목 | 대기 이유 | 재개 조건·작업 |
| --- | --- | --- |
| 미확보 음식점·카페 사진 | [이미지 조사](docs/dining-images.md)의 미확보 업소는 촬영 대상 또는 재사용 조건 미확인 | 대상 일치·이용 조건 확인 후 등록 |
| 행사 자료 | 수집 내용 관리자 검토 대기 | [행사 데이터](docs/events-data.md)의 공식 안내를 재확인하고 검토 |
| 관광 추천 자료 | 실제 장소·관계의 관리자 검토 대기 | [검토 기준](docs/tourism-data.md#검토)에 따라 각각 승인; 운영·예약 조건은 최신 운영기관 안내 확보 후 보완 |
| 사용자 검증 | 참여자 미확보 | 방문객 3명·담당자 2명 확보 후 [완료 기준](spec.md#완료-기준)에 따라 수행 |
| AWS 배포 | 공식 배포 요구 미확인 | 요구 확인 후 범위·설계 결정 |
