# siheung-tourpass

## 소개

시흥시 관광 장소를 추천하고 혜택 이용 후 피드백을 다음 추천에 반영하는 수업용 웹서비스. 추천 장소를 개인 여행 패스에 담고 방문 기록을 보관한다. 혜택 이용권은 시연용·실제 사용 불가다.

![시흥 투어패스 핵심 기능](docs/assets/key-features.svg)

## 기술 스택

Kotlin·Spring Boot 3.5·Spring MVC·JSP/JSTL·Java 17·JDBC·PostgreSQL 16 이상·Maven 3.9. 구현 경계는 [구현 계획](plan.md#기술-구조)을 따른다.

![시흥 투어패스 시스템 아키텍처](docs/assets/architecture.svg)

## 시작하기

명령은 저장소 루트에서 실행한다.

1. Java 17·Maven 3.9·PostgreSQL 16 이상·Python 3을 준비한다. 빈 DB에 아래 순서로 스키마·시연 계정/혜택·실제 관광 자료를 적재한다. 기존 DB에 초기화 SQL을 재실행하지 않는다.

   ```sh
   createdb -U postgres -E UTF8 siheung_tourpass
   psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < backend/sql/schema.sql
   psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < backend/sql/seed.sql
   python3 backend/scripts/tourism-sql.py --replace-demo > /tmp/siheung-tourism.sql
   psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < /tmp/siheung-tourism.sql
   ```

   기존 DB는 초기화 SQL 대신 백업 후 [마이그레이션 디렉터리](backend/sql/migrations)의 SQL 중 아직 적용하지 않은 파일만 번호 순서대로 한 번씩 적용한다. 각 파일의 적용 명령은 `psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < 파일경로`다. 신규 스키마에는 이미 포함되어 있다.

2. DB에서 앱 전용 계정을 만들고 테이블의 SELECT·INSERT·UPDATE·DELETE와 identity 시퀀스의 USAGE 권한을 부여한다.

   ```sql
   CREATE ROLE tourpass LOGIN;
   -- psql에서 \password tourpass로 비밀번호를 설정한다.
   GRANT CONNECT ON DATABASE siheung_tourpass TO tourpass;
   GRANT USAGE ON SCHEMA public TO tourpass;
   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO tourpass;
   GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO tourpass;
   ```

   앱 실행 환경에 연결 정보를 설정한다. 비밀번호는 로컬 환경으로만 전달한다.

   ```sh
   export TOURPASS_DB_URL='jdbc:postgresql://127.0.0.1:5432/siheung_tourpass'
   export TOURPASS_DB_USER='tourpass'
   read -r -s TOURPASS_DB_PASSWORD
   export TOURPASS_DB_PASSWORD
   ```

   외부 Tomcat 배포에서 환경 변수가 없으면 Tomcat JNDI `java:comp/env/jdbc/TourpassDB` DataSource를 사용한다. JNDI 방식에서는 PostgreSQL JDBC 드라이버를 Tomcat `lib`에 제공한다.
3. 같은 환경에서 빌드하고 실행한다. JSP 때문에 JAR 대신 WAR를 사용한다.

   ```sh
   mvn -f backend/pom.xml clean package
   java -jar backend/target/siheung-tourpass.war
   ```

   외부 Tomcat 10.1을 사용하는 경우 WAR를 `webapps`에 복사하고 `bin/catalina.sh run`으로 실행한다. 두 실행 방식 중 하나를 사용한다. 포트는 내장 실행 시 `--server.port=8081`로 변경할 수 있다.

실제 관광 자료의 승인·기존 DB 전환은 [데이터 안내](docs/tourism-data.md#적재)를 따른다.

## 사용 방법

화면은 `http://localhost:8080/siheung-tourpass/`, API는 같은 주소의 `/api/v1`. 시연 계정 `visitor`, `visitor2`, `staff-a`, `staff-b`, `admin`의 초기 암호는 모두 `DemoPass!2026`이다.

| 역할·화면 | 사용 흐름 |
| --- | --- |
| 방문객 | 여행 만들기에서 빈 구성·템플릿 선택 → 카드 탐색·선택·순서 확인 → 이름·날짜 임시 저장 → 이어 만들기·제작 확정 → 내 패스 → 방문 기록 → 사용 완료 → 여행 기록 조회 |
| 카드 탐색 | ‘새 여행 만들기’에서 검색·종류·지역·행사 날짜·취향 찾기·카드 섞기 사용. 가로 스크롤·스와이프·마우스 드래그로 탐색하고 내 여행 덱에서 저장. 비로그인 탐색 가능, 저장 시 방문객 로그인 필요 |
| 행사 | ‘행사 일정 보기’ 또는 여행 만들기에서 날짜 검색·상세 확인. 변경·접수 조건은 공식 안내 링크에서 확인 |
| 선택 혜택 | 상품에서 이용권 발급 → 여행 설정에서 연결 → 담당자에게 코드 제시 → 사용 후 피드백 |
| 프로필 | 계정 정보·화면 설정·로그아웃 |
| 담당자 | 코드 입력 → 배정 혜택·잔여 확인 → 사용 확인 → 이력 조회 |
| 관리자 | 상품·가맹점·혜택·장소·개념·관계 관리, 자료 검토, 사유 입력 후 이용권 취소 |

초기 실제 자료도 카드에서 개인 여행에 담을 수 있다. 추천은 관리자 화면에서 장소와 관계를 각각 승인한 뒤 제공한다.

### API

`GET /api/v1/session`에서 받은 쿠키·CSRF 토큰을 사용한다. 변경 요청에 `X-CSRF-Token`·`Content-Type: application/json`을 전달하며 로그인 후 바뀐 토큰을 사용한다.

## 테스트

[GitHub Actions](.github/workflows/ci.yml)가 push·PR마다 빌드·자동 검사를 실행한다. 검사 명령과 결과는 워크플로·Actions에서 확인한다.

카드 브라우저 검사는 Playwright와 Chrome이 있는 환경에서 `node backend/tests/cards_browser_check.cjs http://localhost:8080/siheung-tourpass`로 실행한다. Playwright가 외부 경로에 설치되어 있으면 `PLAYWRIGHT_MODULE`에 모듈 경로를 지정한다. 검사에는 방문객 시연 계정이 필요하며 생성한 임시 여행은 삭제한다.

## 관련 문서

[요구사항](spec.md) · [구현 계획](plan.md) · [디자인](docs/design.md) · [추천 모델](docs/tourism-semantics.md) · [관광 데이터](docs/tourism-data.md) · [행사 데이터](docs/events-data.md) · [음식점 데이터](docs/dining-data.md) · [음식점 이미지 조사](docs/dining-images.md) · [작업 지침](AGENTS.md)
