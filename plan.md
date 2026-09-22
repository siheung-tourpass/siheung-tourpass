# 구현 계획

## 기술 구조

Kotlin Controller·기존 Java Service·JDBC를 사용하는 MVC Model2. [Maven 실행형 WAR](backend/pom.xml)로 내장/외부 Tomcat 10.1을 지원한다. JSON은 Jackson, 암호는 JDK PBKDF2다.

`브라우저 → SecurityFilter → DispatcherServlet → Kotlin Controller → WebHandler / ApiHandler → Service → Sql → PostgreSQL 16`

| 경로·원본 | 책임 |
| --- | --- |
| [Controller](backend/src/main/kotlin/kr/ac/siheung/tourpass/controller/TourpassController.kt) | 화면·API 매핑 |
| [WebHandler](backend/src/main/java/kr/ac/siheung/tourpass/controller/WebHandler.java) · [ApiHandler](backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java) | 폼·PRG·확인 토큰·시간 표시 / JSON 계약; 요청 트랜잭션에서 인증·CSRF 검사 |
| [SecurityFilter](backend/src/main/java/kr/ac/siheung/tourpass/filter/SecurityFilter.java) | 인증·역할·CSRF 검사 함수·캐시·보안 헤더 |
| [Service](backend/src/main/java/kr/ac/siheung/tourpass/service) | 화면/API 공통 업무 규칙; AuthService 역할 판정·Input 단일 입력 검증·AdminService 관리 필드 |
| [Database](backend/src/main/java/kr/ac/siheung/tourpass/config/Database.java) · [Sql](backend/src/main/java/kr/ac/siheung/tourpass/dao/Sql.java) | 연결·트랜잭션·잠금·rollback·매개변수 SQL·DB 값 변환 |
| [SQL](backend/sql/schema.sql) · [seed](backend/sql/seed.sql) | 관계·제약 / 시연 자료·계정 |
| [frontend](frontend/WEB-INF/views) | JSP·EL/JSTL·이스케이프; SQL·업무 판정·스크립틀릿 금지 |
| [설정](backend/src/main/resources/application.properties) · [web.xml](frontend/WEB-INF/web.xml) | 내장/외부 실행·세션·JNDI |

- 서버·SQL·데이터·스크립트·검사는 `backend/`, JSP·정적 자산은 `frontend/`, 세부 문서는 `docs/`에 둔다. 로컬 `.runtime/` 경로를 유지한다.
- JourneyTemplates는 지정 장소 코드로 시작 구성을 정의하며 탐색 가능한 장소에 대해 제공 여부를 판정한다. `/explore`와 새 여행 구성은 `journey-builder.jspf`·`journey-builder.js`의 통합 카드 보드를 공유한다. JSP가 앞뒷면을 출력하고 JavaScript가 선택·전환 상태를 제어한다. 카드 덱과 여행 기록의 중앙 스크롤 계산은 `card-scroll.js`를 공유한다. 가로 스크롤 스냅을 사용하는 세로형 사진 카드와 선택·저장 대화상자를 사용한다. OntologyService.browsePlaces는 승인 관광지와 검토 대기 실제 자료를 탐색용으로 제공하며 추천 승인 검사는 유지하며 여행 담기는 검토 대기 실제 관광지도 허용한다. 관광지·행사·가맹점 선택·목록 순서를 유지하며 검색·종류·지역·행사 날짜 필터와 카드 섞기를 제공하고 기존 추천 API로 관광지 카드를 강조한다. 가맹점 조건은 CatalogService, 행사 안내는 EventCatalog를 재사용한다. 여행 항목은 journey_place의 카드 종류·고유 키로 구분하며 기존 placeId API를 유지한다. 저장은 JourneyService에서 선택 카드를 재검증하며 한 트랜잭션으로 처리한다.
- JourneyService는 PLANNING·CONFIRMED·ARCHIVED 상태를 사용한다. 제작 중 수정은 기존 장소 스냅샷·기록을 유지하며 선택 변경·순서·확정을 한 트랜잭션으로 저장한다. 삭제는 본인 제작 중 패스만 허용한다. 기존 DB는 `003-journey-confirmed.sql`로 상태 제약을 확장하며 기존 상태는 유지한다.
- 여행·장소 기록은 혜택 이용권과 별도 저장한다. JourneyService는 부모 여행 행 잠금으로 중복 담기·순서 변경을 직렬화하며, 단일·일괄 담기의 카드 저장 로직을 공유한다.
- 이용권 연결·해제 이력은 여행 설정과 같은 트랜잭션에서 보존한다. 기존 연결의 시각은 추정하지 않고 미확인으로 표시한다.
- 장소 사진은 정적 자산과 [메타데이터](backend/src/main/resources/place-images.json)를 함께 배포한다. PlaceImages가 장소 코드로 추천·상세 응답에 연결한다.
- 행사 안내는 EventCatalog가 [수집 JSON](backend/data/siheung-events.json)을 읽어 한국 날짜 기준으로 분류한다. `/events`와 새 여행 화면이 카드 뷰를 공유하며 행사 원본은 JSON으로 유지하고 여행 선택은 JourneyService에 위임한다. 관광 추천 점수에는 연결하지 않는다.
- 온톨로지 변경·검토·버전 갱신은 같은 Service 트랜잭션에서 처리한다. 추천 계산은 [추천 모델](docs/tourism-semantics.md)을 따른다.
- 로컬 PostgreSQL 16 시연 필수. CI만 PostgreSQL 16 컨테이너를 사용하며 JPA·운영 Docker·RDS·로드밸런서·오토스케일링·S3·CloudFront·자동 배포는 제외한다.

## 남은 작업

- 행사 자료는 내용 검토 및 일부 이미지 이용허락 확인 대기다. [행사 데이터](docs/events-data.md)의 미확인 근거·이용 조건을 확보한 뒤 갱신한다.
- 실제 관광 장소·관계는 관리자 검토 대기다. [데이터 안내](docs/tourism-data.md#검토)의 근거를 검토한 뒤 각각 승인한다. 운영·예약 조건이 필요하면 최신 운영기관 안내 확보 후 보완한다.
- 사용자 검증은 참여자 미확보로 대기한다. 방문객 3명·담당자 2명 확보 후 [완료 기준](spec.md#완료-기준)에 따라 수행한다.
- AWS는 공식 배포 요구 확인 후 범위·설계를 결정한다.
