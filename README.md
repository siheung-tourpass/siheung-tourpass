# siheung-tourpass — 디지털 투어패스

## 소개

시흥시의 장소를 나만의 여행 패스에 구성하고, 추천 장소를 담고, 방문 기록을 아카이브로 보관하는 수업용 서비스다.

여행 패스는 만료되지 않는다.

선택 기능인 혜택 이용권과 가맹점 혜택은 **시연용·실제 사용 불가**다.

JSP 화면·폼과 JSON API가 같은 Service를 사용한다.

`backend/`는 서버·SQL·데이터·검사, `frontend/`는 JSP·정적 자산, `docs/`는 문서·참고 자료다. 아래 명령은 저장소 루트에서 실행한다.

## 기술 스택

Kotlin, Spring Boot 3.5, Spring MVC, JSP·JSTL, Java 17 JVM, JDBC, MySQL 8.0.16 이상, Maven 3.9.

내장 Tomcat 10.1을 포함한 실행형 WAR를 사용한다.

JSON은 Jackson, 암호 해시는 JDK PBKDF2-HMAC-SHA256(210,000회·16바이트 salt)을 사용한다.

서버·DB 시각은 UTC다.

## 시작하기

1. Java 17·Maven 3.9·MySQL 8.0을 준비한다. 빈 DB에 아래 순서로 스키마와 시연 자료를 적재한다. 기존 DB에 초기화 SQL을 재실행하지 않는다.

   ```sh
   mysql -uroot -p -e 'CREATE DATABASE siheung_tourpass CHARACTER SET utf8mb4'
   mysql -uroot -p siheung_tourpass < backend/sql/schema.sql
   mysql -uroot -p siheung_tourpass < backend/sql/seed.sql
   ```

   기존 DB를 유지하며 업데이트할 때는 백업 후 [추가 마이그레이션](backend/sql/migrations/001-journeys.sql)을 한 번만 적용한다. 신규 스키마에는 이미 포함되어 있다.

   ```sh
   mysql -uroot -p siheung_tourpass < backend/sql/migrations/001-journeys.sql
   ```
2. DB에서 앱 전용 계정을 만들고 이 DB의 SELECT·INSERT·UPDATE·DELETE 권한을 부여한다. 앱 실행 환경에 연결 정보를 설정한다. 비밀번호는 로컬 환경으로만 전달한다.

   ```sh
   export TOURPASS_DB_URL='jdbc:mysql://127.0.0.1:3306/siheung_tourpass?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
   export TOURPASS_DB_USER='tourpass'
   read -r -s TOURPASS_DB_PASSWORD
   export TOURPASS_DB_PASSWORD
   ```

   외부 Tomcat 배포에서 환경 변수가 없으면 Tomcat JNDI `java:comp/env/jdbc/TourpassDB` DataSource를 사용한다. JNDI 방식에서는 MySQL Connector/J를 Tomcat `lib`에 제공한다.
3. 같은 환경에서 빌드하고 실행한다. JSP 때문에 JAR 대신 WAR를 사용한다.

   ```sh
   mvn -f backend/pom.xml clean package
   java -jar backend/target/siheung-tourpass.war
   ```

   외부 Tomcat 10.1을 사용하는 경우 WAR를 `webapps`에 복사하고 `bin/catalina.sh run`으로 실행한다. 두 실행 방식 중 하나를 사용한다. 포트는 내장 실행 시 `--server.port=8081`로 변경할 수 있다.
4. 선택적으로 실제 관광 자료를 적재한다. 기존 [JSON](backend/data/siheung-tourism.json)을 그대로 변환하며 장소·관계는 DRAFT로 저장한다. 신규 DB에서 1회 실행한다. 관리자가 장소와 관계를 각각 승인하기 전에는 추천에서 제외된다.

   ```sh
   python3 backend/scripts/tourism-sql.py > /tmp/siheung-tourism.sql
   mysql -uroot -p siheung_tourpass < /tmp/siheung-tourism.sql
   ```

## 사용 방법

화면은 `http://localhost:8080/siheung-tourpass/`, API는 같은 주소의 `/api/v1`이다. 시연 계정 `visitor`, `visitor2`, `staff-a`, `staff-b`, `admin`의 초기 암호는 모두 `DemoPass!2026`이다.

- 방문객: 로그인 → 나의 여행 패스 생성 → 추천·장소 상세에서 담기 → 순서 구성 → 방문 날짜·메모·만족도 → 설정에서 여행 완료·아카이브 → 지난 여행 다시 보기.
- 혜택 선택: 상품 상세에서 이용권 발급 → 여행 설정에서 본인 이용권 연결 → 코드 제시 → 담당자 확인 → 사용 피드백. 직접 남긴 방문 기록과 확인된 혜택 사용 이력은 구분한다.
- 담당자: 로그인 → 배정 가맹점·코드 입력 → 자기 혜택과 잔여 확인 → 사용 확인 → 가맹점 이력. 확인은 5분 이내에 한 번만 가능하다.
- 관리자: 로그인 → 상품/가맹점/혜택·장소/개념/관계 등록·수정·비활성화·자료 검토·패스 사유 취소. 의미 자료를 수정하면 다시 검토해야 한다.

API 계약은 [API 요청 처리](backend/src/main/java/kr/ac/siheung/tourpass/controller/ApiHandler.java)과 [혜택 검사](backend/tests/api_check.py)·[여행 검사](backend/tests/journey_check.py)가 원본이다. `GET /session`의 쿠키·CSRF 토큰을 사용하고 변경 요청에는 현재 `X-CSRF-Token`과 `Content-Type: application/json`을 보낸다. 로그인 성공 후 토큰이 바뀐다.

## 테스트

**별도의 빈 테스트 DB**에 스키마·시연 자료를 적재하고 해당 DB로 앱을 실행한다. 테스트는 패스·평가·관리 자료를 생성하고 변경한다. 기본 URL이 다르면 마지막 인자로 전달한다.

```sh
mvn -f backend/pom.xml package
backend/scripts/check-boundaries.sh
python3 backend/tests/api_check.py http://localhost:8080/siheung-tourpass/api/v1
python3 backend/tests/journey_check.py http://localhost:8080/siheung-tourpass/api/v1
python3 backend/tests/admin_check.py http://localhost:8080/siheung-tourpass/api/v1
# 화면 검사는 위 API 검사와 별개의 새 시연 DB로 실행
python3 backend/tests/web_check.py http://localhost:8080/siheung-tourpass
```

실제 자료를 선택 적재한 후 `python3 backend/tests/db_check.py /path/to/test-mysql.sock`으로 저장 건수·CHECK 제약을 검사한다(로컬 테스트 root 계정 기준). 이어 `python3 backend/tests/race_check.py <API URL>`로 취소·가맹점/혜택 비활성화와 사용 경쟁을 검사한다. MySQL·앱을 모두 중지 후 같은 DB로 재실행하고 `python3 backend/tests/restart_check.py <API URL>`로 보존을 확인한다.

## 관련 문서

[요구사항](spec.md) → [계획](plan.md). [디자인 기준](docs/design.md) · [DB 스키마](backend/sql/schema.sql) · [MVC 설계](docs/mvc-design.md) · [추천 의미 모델](docs/tourism-semantics.md) · [관광 데이터 수집](docs/tourism-data.md) · [제안서](docs/proposal.md) · [조사](docs/research.md) · [평가 기준](docs/evaluation.md) · [작업 지침](AGENTS.md)
