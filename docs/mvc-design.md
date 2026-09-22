# MVC 구현 안내

[요구사항](../spec.md)을 화면과 API에서 같은 Service로 처리한다.

`브라우저 → SecurityFilter → DispatcherServlet → Kotlin Controller → WebHandler / ApiHandler → Service → Sql → MySQL`

화면은 Service 결과를 JSP에 전달한다. JSP에는 업무 판정·SQL·Java 스크립틀릿을 두지 않는다.

| 원본 | 확인할 내용 |
| --- | --- |
| [Kotlin Controller](../backend/src/main/kotlin/kr/ac/siheung/tourpass/controller/TourpassController.kt) | Spring MVC 화면/API 매핑 |
| [WebHandler](../backend/src/main/java/kr/ac/siheung/tourpass/controller/WebHandler.java) | 화면 경로·폼 입력·세션·PRG·확인 토큰·한국 시간 변환 |
| [ApiHandler](../backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java) | JSON 경로·메서드·응답 계약 |
| [SecurityFilter](../backend/src/main/java/kr/ac/siheung/tourpass/filter/SecurityFilter.java) | UTF-8·현재 계정/역할·CSRF·캐시/보안 헤더 |
| [JSP](../frontend/WEB-INF/views/page.jsp) | 직접 접근 불가 화면·EL/JSTL·출력 이스케이프 |
| [Application](../backend/src/main/kotlin/kr/ac/siheung/tourpass/TourpassApplication.kt) · [설정](../backend/src/main/resources/application.properties) | 내장/외부 Tomcat 시작·Filter 등록·세션 설정 |
| [web.xml](../frontend/WEB-INF/web.xml) | 외부 Tomcat 세션·JNDI 선언 |
| [Database](../backend/src/main/java/kr/ac/siheung/tourpass/config/Database.java) · [Sql](../backend/src/main/java/kr/ac/siheung/tourpass/dao/Sql.java) | 공통 연결·트랜잭션·잠금·rollback·PreparedStatement |
| [JourneyService](../backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java) · [여행 JSP](../frontend/WEB-INF/views/journeys.jspf) | 개인 구성·장소 스냅샷·방문 기록·아카이브·소유권·순서 잠금 |
| [PassService](../backend/src/main/java/kr/ac/siheung/tourpass/service/PassService.java) · [RedemptionService](../backend/src/main/java/kr/ac/siheung/tourpass/service/RedemptionService.java) | 발급 조건 보존·소유권·사용/취소 경쟁·원자성 |
| [화면 검사](../backend/tests/web_check.py) · [API 검사](../backend/tests/api_check.py) · [관리 검사](../backend/tests/admin_check.py) · [경쟁 검사](../backend/tests/race_check.py) | 실행 가능한 동작·권한·예외 검증 |

추가 계층이나 예정 클래스 목록은 유지하지 않는다. DB·추천 규칙은 [스키마](../backend/sql/schema.sql)와 [추천 구현 안내](tourism-semantics.md), 실행은 [README](../README.md)에 둔다.
