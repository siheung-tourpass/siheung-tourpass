# siheung-tourpass

## 소개

시흥시 관광 장소를 추천하고 혜택 이용 후 피드백을 다음 추천에 반영하는 수업용 웹서비스다. 추천 장소를 개인 여행 패스에 담고 방문 기록을 보관하며, 혜택 이용권은 시연용(실제 사용 불가)이다. 기획 근거와 검증 과제는 [지역 문제와 사용자 검증](docs/presentation/project-validation.md)을 따른다.

![시흥 투어패스 핵심 기능](docs/assets/key-features.svg)

### 주요 화면

모바일 `360×784` 화면을 2배 해상도로 캡처했다(클릭 시 원본 표시). 단계별 캡처·UX 검토는 [핵심 유저 플로우](docs/04-test/02-user-flows.md)를 따른다.

| 서비스 소개 | 상품 선택 | 여행 덱 구성 |
| --- | --- | --- |
| [![관광지·행사 카드와 서비스 소개](docs/assets/landing.png)](docs/assets/landing.png) | [![혜택 상품별 추천 여행 구성과 여행 시작 버튼](docs/assets/tourpass-products.png)](docs/assets/tourpass-products.png) | [![시작 장소 주변의 후보 카드와 직선거리 안내](docs/assets/journey-builder.png)](docs/assets/journey-builder.png) |
| 관광지·행사를 둘러보고 여행 만들기를 시작한다. | 상품의 혜택 장소를 담아 여행을 시작한다. | 주변 후보를 비교하며 장소를 추가한다. |

| 여행 확인·구매 진입 | 내 투어패스 | 방문 기록 |
| --- | --- | --- |
| [![여행 이름·날짜와 추천 투어패스 가격 확인](docs/assets/journey-review.png)](docs/assets/journey-review.png) | [![현재 투어패스의 남은 시간과 가맹점 제시 QR](docs/assets/my-tourpass.png)](docs/assets/my-tourpass.png) | [![방문 날짜·만족도·나만의 메모를 저장하는 여행 기록](docs/assets/journey-record.png)](docs/assets/journey-record.png) |
| 이름·날짜와 추천 상품·가격을 확인하고 시연 구매로 이동한다. | 남은 시간과 가맹점 제시 QR을 확인한다. | 방문 날짜·만족도·메모를 남겨 보관한다. |

## 기술 스택

Spring Boot 3.5 · Spring MVC · JSP/JSTL · Java 17 · JDBC · PostgreSQL 16+ · Maven 3.9 · Leaflet/OpenStreetMap. 구현 경계는 [기술 구조](docs/03-tech/01-architecture.md#아키텍처와-경계)를 따른다.

![시흥 투어패스 시스템 아키텍처](docs/assets/architecture.svg)

## 시작하기

명령은 저장소 루트에서 실행한다.

### Docker 실행

로컬 개발·시연용으로 앱과 DB를 함께 실행한다(운영 배포 제외).

```sh
docker compose up --build
```

`Started TourpassApplication` 로그가 표시되면 `http://localhost:8080/siheung-tourpass/`에 접속한다(포트 변경: `TOURPASS_PORT=8081 docker compose up --build`). 카카오 로그인은 [카카오 로그인 설정](#카카오-로그인-설정)의 환경 변수를 루트 `.env`에 적고 재실행한다.

| 작업 | 명령어 |
| --- | --- |
| 백그라운드 실행 / 중지 / 재시작 | `docker compose up -d` / `docker compose stop` / `docker compose start` |
| 컨테이너 삭제(DB 유지) / 전체 초기화 | `docker compose down` / `docker compose down -v` (이미지 삭제 `--rmi local`) |
| 코드·행사·음식점 JSON 반영 | `docker compose up -d --build app` |
| 로그 / DB 접속 | `docker compose logs -f app` / `docker compose exec db psql -U tourpass -d siheung_tourpass` |

빈 DB 볼륨일 때만 스키마·시연 계정·관광 자료·[투어패스 시연 자료](docs/data-collection/tourpass-data.md)를 적재하고 [가상 자료 삭제 SQL](backend/sql/remove-demo.sql)을 실행한다. 실제 관광지는 검토 대기로 적재되므로 시연 전 관리자 화면에서 승인한다([관광 데이터 적재](docs/data-collection/tourism-data.md#적재)).

### 직접 설치

1. Java 17 · Maven 3.9 · PostgreSQL 16+ · Python 3 · `createdb` · `psql`을 준비하고 빈 DB에 초기 자료를 적재한다(기존 DB 재실행 금지).

   ```sh
   createdb -U postgres -E UTF8 siheung_tourpass
   psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < backend/sql/schema.sql
   psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < backend/sql/seed.sql
   python3 backend/scripts/tourism-sql.py --replace-demo > /tmp/siheung-tourism.sql
   psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < /tmp/siheung-tourism.sql
   python3 backend/scripts/tourpass-sql.py > /tmp/siheung-tourpass.sql
   psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < /tmp/siheung-tourpass.sql
   ```

   기존 DB는 백업 후 [마이그레이션 디렉터리](backend/sql/migrations)의 미적용 SQL만 번호순으로 1회씩 적용한다(`psql -X -v ON_ERROR_STOP=1 -U postgres -d siheung_tourpass < 파일경로`). 새 테이블 추가 시 2단계 권한 부여도 재실행한다. 새 장소는 `tourism-sql.py --place-code 장소코드`로, 새 투어패스 상품·가맹점은 `tourpass-sql.py` 재적용으로 추가한다([투어패스 적재](docs/data-collection/tourpass-data.md#적재)).
2. 앱 전용 DB 계정에 권한을 부여하고 연결 환경 변수를 설정한다.

   ```sql
   CREATE ROLE tourpass LOGIN;
   -- psql에서 \password tourpass로 비밀번호를 설정한다.
   GRANT CONNECT ON DATABASE siheung_tourpass TO tourpass;
   GRANT USAGE ON SCHEMA public TO tourpass;
   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO tourpass;
   GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO tourpass;
   ```

   ```sh
   export TOURPASS_DB_URL='jdbc:postgresql://127.0.0.1:5432/siheung_tourpass'
   export TOURPASS_DB_USER='tourpass'
   read -r -s TOURPASS_DB_PASSWORD
   export TOURPASS_DB_PASSWORD
   ```

   외부 Tomcat에서 환경 변수가 없으면 JNDI `java:comp/env/jdbc/TourpassDB`를 사용하며 Tomcat `lib`에 PostgreSQL JDBC 드라이버를 둔다.
3. WAR를 빌드·실행한다(포트 변경: `--server.port=8081`, 외부 Tomcat 10.1은 `webapps` 복사 후 `bin/catalina.sh run`).

   ```sh
   mvn -f backend/pom.xml clean package
   java -jar backend/target/siheung-tourpass.war
   ```

실제 관광 자료의 승인·전환은 [데이터 안내](docs/data-collection/tourism-data.md#적재)를 따른다.

### 카카오 로그인 설정

[카카오 로그인 REST API 안내](https://developers.kakao.com/docs/ko/kakaologin/rest-api)에 따라 로그인 활성화 후 Redirect URI(로컬 기본 `http://localhost:8080/siheung-tourpass/login/kakao/callback`, 배포 시 HTTPS 주소)를 등록한다(회원번호만 사용, 이메일·프로필 동의 불필요).

로컬 환경에 `TOURPASS_KAKAO_CLIENT_ID`, `TOURPASS_KAKAO_REDIRECT_URI`(Client Secret 사용 시 `TOURPASS_KAKAO_CLIENT_SECRET` 포함)를 설정하고 재시작한다. 미설정 시 로그인 버튼은 준비 중으로 표시된다.

## 사용 방법

화면은 `http://localhost:8080/siheung-tourpass/`, API는 `/api/v1`이다. 시연 계정 `visitor`, `visitor2`, `staff-a`, `staff-b`, `admin`의 초기 암호는 `DemoPass!2026`이다.

| 작업 | 사용 흐름 |
| --- | --- |
| 여행 구성 | 여행 만들기 → 상품 선택 또는 직접 고르기 → 덱 구성 → 여행 확인 → 이름·날짜 입력 → 임시 저장·확정 |
| 공개 탐색 | 여행 카드 둘러보기 → 검색·종류·지역·행사 날짜 탐색 → 상세·공식 안내 확인(저장 시 방문객 로그인) |
| 여행 기록 | 내 투어패스 → 진행 중 여행 → 방문 기록 → 사용 완료 → 내 여행에서 사진 등록·포토카드 인쇄 |
| 투어패스 구매·이용 | 여행 확인 `확정하고 투어패스 구매` → 시연 결제 → 혜택 배지 확인 → 내 투어패스 코드 제시 → 사용 이력 피드백 |
| 관광지 평가 | 관광지 상세 → 만족도·의견 저장·수정·삭제 |
| 가맹점 담당자 | 코드 입력 → 배정 혜택·잔여 확인 → 사용 확인 → 결과·이력 조회 |
| 관리자 | 관광·혜택 관리·검토, 통계 조회, 사유 입력 후 이용권 취소, 관광 관리 → 상세 → 카드 출력·인쇄 |
| 카카오 로그인 | 로그인 → 카카오톡으로 로그인 → 동의 → 방문객 홈(최초 로그인 시 별도 방문객 계정 생성, 기존 계정과 분리) |
| 계정 | 프로필 → 계정 정보·화면 설정·로그아웃 |

담기 조건은 [여행 구성](docs/01-func/02-visitor.md#담기와-보존), 추천 승인은 [데이터 적재](docs/data-collection/tourism-data.md#적재), 포토카드(A4 세로·배율 100%·머리글/바닥글 해제·전체 서비스 경로 QR) 정책은 [카드 출력](docs/01-func/04-admin.md#카드-출력)을 따른다.

### API

[Scalar](http://localhost:8080/siheung-tourpass/api-docs)에서 문서를 확인하고 `GET /api/v1/` 링크에서 탐색을 시작한다. 보호 요청은 Basic 인증(시연 외 HTTPS 필수), 변경 요청은 `X-CSRF-Token: api`를 전달한다.

Bruno에서 [컬렉션](backend/bruno)(`Local` 환경)의 `Auth / Index`와 역할별 `Select`를 실행하면 인증·CSRF 헤더가 설정된다. 변경 요청은 [별도 테스트 DB](#테스트)에서 실행한다. API 변경 시 [명세 원본](frontend/assets/api-docs/openapi.yaml)을 갱신하고 [명세 검사](backend/tests/python/openapi_check.py)를 실행한다.

## 테스트

[GitHub Actions](.github/workflows/ci.yml)가 push·PR마다 빌드·검사를 실행하며 초기화·실행 명령의 원본이다. 테스트는 DB를 변경하므로 시연 DB와 분리한다. 검증 범위는 [테스트 명세](docs/04-test/01-verification.md#검증-전략), 판정은 [완료 기준](docs/01-func/01-common.md#완료-기준)을 따른다.

브라우저 검사는 Node.js · Playwright(`PLAYWRIGHT_MODULE` 지정 가능) · Chrome과 시연 계정이 있는 테스트 서버가 필요하다.

| 검사 | 명령 |
| --- | --- |
| 카드 탐색·선택·저장 | `node backend/tests/browser/cards_browser_check.cjs http://localhost:8080/siheung-tourpass` |
| 빈 검색·구매 없이 확정·오류 복구 | `node backend/tests/browser/ux_flow_check.cjs http://localhost:8080/siheung-tourpass` |
| 여행 사진 업로드·교체·삭제·소유권 | `node backend/tests/browser/journey_photos_check.cjs http://localhost:8080/siheung-tourpass` |
| 핵심 흐름·단계별 캡처(실제 자료 적재 DB) | `node backend/tests/browser/user_flows_check.cjs http://localhost:8080/siheung-tourpass .runtime/user-flows` |
| 관리자·포토카드 출력 | `node backend/tests/browser/admin_browser_check.cjs http://localhost:8080/siheung-tourpass` |

## 관련 문서

- [작업 지침](AGENTS.md): 작업 순서·문서 관리.
- [이슈·PR 작성 규칙](docs/collaboration.md): 이슈·PR 제목과 본문 작성 방법.

### 발표·사용자 검증

- [최종 발표 준비 기준](docs/presentation/final-presentation.md): 발표·제출 조건·심사기준·자료 구성·점검.
- [지역 문제와 사용자 검증](docs/presentation/project-validation.md): 지역 문제 가설·기능 연결·검증 과제·기록.
- [거북섬 투어패스 검토 과제](docs/presentation/시흥%20거북섬%20투어패스%20도입을%20위한%20검토%20과제.pdf): 투어패스 도입 검토 참고 자료.

### 데이터 수집·검토

- [데이터 수집 공통 기준](docs/data-collection/common.md): 공통 출처·좌표·이미지 기준.
- [관광 데이터](docs/data-collection/tourism-data.md): 관광 적재·승인·운영 조건 검토·사진 배포.
- [행사 데이터](docs/data-collection/events-data.md): 행사 일정·장소 연결·이미지·배포 조건.
- [음식점 데이터](docs/data-collection/dining-data.md): 공식 지정 업소의 공개·분류·이미지·배포 조건.
- [음식점 이미지 조사](docs/data-collection/dining-images.md): 미등록 사진 후보 근거·미확보 사유·재개 조건.
- [투어패스 시연 자료](docs/data-collection/tourpass-data.md): 가상 가맹점·상품·가격 자료의 성격과 적재.

### 문서 분류 기준

<!-- doc-standard:start — doc-standard에서 동기화한다. 이 구간은 직접 고치지 않는다. -->
| 위치 | 담는 내용 |
| --- | --- |
| [`docs/01-func/`](docs/01-func/README.md) | 범위, 역할별 기능, 업무 정책, 완료 기준 |
| [`docs/02-uxui/`](docs/02-uxui/01-design.md) | 화면 배치·조작·디자인 기준·시각 자산 |
| [`docs/03-tech/`](docs/03-tech/README.md) | 아키텍처, 소스 구조, 데이터 모델, REST/IPC 계약 |
| [`docs/04-test/`](docs/04-test/README.md) | 검증 방법, 판정 기준, 최신 판정과 증거 |
| `docs/` 루트·주제 폴더 | 비명세 자료(과제 안내, 보고서, 발표, 협업 규칙, 자료 수집 기록) |
| 코드 저장소 루트 README | 로컬 설치·실행·테스트 명령 |

API 상세 원본은 OpenAPI·라우터·DTO이며 명세에는 원본 위치와 한계만 적는다. 배포 절차는 `05-ops/`(없으면 기술 명세)에 두고 로컬 실행 명령은 README를 링크한다.
<!-- doc-standard:end -->
