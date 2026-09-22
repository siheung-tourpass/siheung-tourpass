# 구현 계획

[요구사항](spec.md)이 구현 기준이다.

## 기능 우선순위

- 메인 기능은 [요구사항의 핵심 흐름](spec.md#제품-범위)에 따른 온톨로지 기반 관광 추천과 이용 후 피드백 수집·다음 추천 반영이다. 기능 10~12와 검증 기준 C8~C9로 확인한다.
- 개인 여행 패스·아카이브는 추천 장소의 계획·기록을 지원하고, 시연 혜택 이용권은 선택 기능으로 제공한다.

## 기술 스택

- Kotlin·Spring Boot 3.5·Spring MVC·JSP·EL/JSTL 3.0, Java 17 JVM·서버 렌더링·폼 요청.
- 기존 Java Service·JDBC·MySQL 8.0·UTF-8·DB UTC/화면 한국 시간을 유지한다.
- Maven 실행형 WAR·내장 Tomcat 10.1. JSON은 Jackson, 암호는 JDK PBKDF2.
- 로컬 MySQL 시연 필수. JPA·RDS·Docker·로드밸런서·오토스케일링·S3·CloudFront·CI/CD는 범위 밖이다.

## 디렉터리 구성

- `backend/`: Maven 프로젝트·서버 소스·SQL·관광 JSON·스크립트·검사·빌드 결과.
- `frontend/`: 기존 webapp의 JSP·배포 설정·정적 자산. Maven WAR의 웹 소스 경로로 연결한다.
- 루트: `spec.md`·`plan.md`와 공통 안내·설정.
- `docs/`: 그 외 문서와 `assets/`의 참고 PDF. 루트 README의 명령은 저장소 루트 기준으로 갱신한다.
- 숨김 로컬 DB·런타임 디렉터리 `.runtime/`는 기존 경로를 보존한다.

## Spring Boot 전환

- Kotlin Controller가 Spring DispatcherServlet을 통해 화면·API 요청을 받고 기존 Java 요청 처리·Service를 재사용한다. 기존 Servlet 등록은 제거한다.
- 보안 필터는 Spring 등록으로 옮기고 MVC 요청 경로에 맞게 API 판별을 수정한다. CSRF·세션·권한·트랜잭션·응답 계약은 유지한다.
- JSP 지원을 위해 실행형 WAR를 사용한다. `java -jar`와 외부 Tomcat 배포 경로를 제공한다.

## 여행 패스 전환

- 기존 발급·사용 계약을 혜택 이용권으로 유지하고, 여행 패스·장소 기록은 별도 테이블과 [JourneyService](backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java)로 구현한다.
- JSP 폼과 `/me/journeys` API가 이름·날짜·담기·순서·기록·아카이브·이용권 연결의 소유권 및 입력 검증을 공유한다. 부모 여행 행 잠금으로 중복 담기와 순서 변경을 직렬화한다.
- 기존 DB는 [추가 마이그레이션](backend/sql/migrations/001-journeys.sql)을 적용한다. 신규 DB는 스키마만 사용한다.
- 홈은 관광 추천을 우선 안내하고 방문객 로그인 도착 화면은 여행 패스를 중심으로 구성한다. 추천·장소 상세에서 담고, 여행 상세에서 선택 혜택 이용권과 사용 이력으로 이동한다.

## 구현과 원본

- [MVC 구현](docs/mvc-design.md): 화면과 API가 기존 Service·트랜잭션·권한 규칙을 공유한다. JSP는 조회 결과만 렌더링한다.
- 관리 입력 필드는 AdminService에 모으고, 온톨로지 변경·검토와 버전 갱신은 같은 Service 호출·트랜잭션에서 완료한다.
- 필터·화면·API의 역할 판정은 AuthService, MySQL 불리언 변환은 Sql로 통합한다. 2026-10-06 WAR 빌드·경계·API·여행·관리·JSP 폼 검사 통과.
- [스키마](backend/sql/schema.sql)·[시연 자료](backend/sql/seed.sql)·[API](backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java): 저장 구조·초기 계정·JSON 계약의 원본.
- [추천 구현](docs/tourism-semantics.md): 어노테이션·온톨로지·시맨틱 레이어·피드백 규칙의 코드/검사 안내.
- [실제 관광 자료](docs/tourism-data.md): JSON→DRAFT 적재, 장소와 관계의 개별 관리자 검토.
- [Figma](https://www.figma.com/design/0dXeoLqriD0iYTPrNdK5ic/siheung-tourpass): 화면 설계 참고. 실행 화면의 원본은 [JSP](frontend/WEB-INF/views/page.jsp).
- [README](README.md): 초기화·실행·사용·검사. 제안서와 프로젝트 평가는 [평가 기준](docs/evaluation.md).

## 화면 개편

- [디자인 기준](docs/design.md)에 따라 공통 JSP 셸·홈·추천·여행·혜택·로그인·담당자·관리 화면을 같은 컴포넌트로 정리한다. 기존 요청 경로·폼 필드·CSRF·업무 규칙을 유지한다.
- [공통 CSS](frontend/assets/style.css)와 [토큰](frontend/assets/tokens.css)으로 반응형·선택·포커스·오류·비활성 상태를 공유한다.

## 남은 검증

자동 검사는 [README](README.md#테스트)의 실행 코드로 유지한다.

- 실제 관광 자료는 [수집 안내](docs/tourism-data.md)에 따라 장소·관계를 관리자 검토한다. 승인 전 추천에서 제외하고 오래된 체험·가족 근거는 기관 최신 안내로 재확인한다.

## 미확정 사항

- 제안서 팀명·팀원 실명·개발 기간: 실제 정보 확인 후 [초안](docs/proposal.md)의 임시 팀명·4인·8주 가정을 갱신한다.
- AWS 배포: 공식 요구 확인 후 설계를 재개하고 구현에 영향이 있으면 [요구사항](spec.md)을 수정한다.
