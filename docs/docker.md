# Docker 실행 안내 — 시흥 투어패스

[문서 안내](../README.md#관련-문서)

Java·Maven·PostgreSQL을 설치하지 않고 로컬에서 앱과 DB를 함께 실행한다. Docker와 Docker Compose가 필요하며 명령은 저장소 루트에서 실행한다. 로컬 개발·시연용 구성이며 운영 배포에 사용하지 않는다.

## 실행

```sh
docker compose up --build
```

처음에는 이미지 빌드로 몇 분 걸릴 수 있다. 로그에 `Started TourpassApplication`이 표시되면 `http://localhost:8080/siheung-tourpass/`에 접속한다. 시연 계정은 [사용 방법](../README.md#사용-방법)을 따른다. 이후에는 코드가 바뀌지 않았다면 `--build` 없이 실행한다.

8080 포트를 이미 사용 중이면 포트를 지정한다.

```sh
TOURPASS_PORT=8081 docker compose up --build
```

## 명령어

| 명령어 | 용도 |
| --- | --- |
| `docker compose up` | 빌드 없이 실행. 로그를 터미널에 표시하며 `Ctrl+C`로 중지 |
| `docker compose up -d` | 백그라운드로 실행 |
| `docker compose stop` | 컨테이너만 중지 |
| `docker compose start` | 중지한 컨테이너 다시 시작 |
| `docker compose down` | 컨테이너 삭제. DB 데이터는 유지 |
| `docker compose ps` | 실행 상태 확인 |
| `docker compose logs -f app` | 앱 로그 보기. `Ctrl+C`로 빠져나와도 앱은 계속 실행 |
| `docker compose logs -f db` | DB 로그 보기 |
| `docker compose exec db psql -U tourpass -d siheung_tourpass` | DB 접속. `\q`로 종료 |

## 변경 반영

| 바꾼 내용 | 명령어 |
| --- | --- |
| Java·JSP·CSS·JS, 행사·음식점 JSON | `docker compose up -d --build app` |
| `schema.sql`·`seed.sql`, 관광 JSON | `docker compose down -v` 후 `docker compose up -d --build` |

프론트엔드 파일도 WAR에 포함되므로 화면만 고쳐도 다시 빌드한다. 초기화 SQL은 DB 볼륨이 비어 있을 때만 실행된다.

## 초기화

| 명령어 | 용도 |
| --- | --- |
| `docker compose down -v` | 컨테이너와 DB 데이터 삭제. 다음 실행 때 초기 자료로 다시 생성 |
| `docker compose down -v --rmi local` | 빌드한 이미지까지 삭제. 다음 실행에 `--build` 필요 |

`-v`를 붙이면 여행·이용권·관광지 승인 내역이 모두 삭제된다.

## 초기 자료

스키마·시연 자료·관광 자료를 자동으로 적재한다. [직접 설치 절차](../README.md#시작하기)와 달리 관광 자료를 `--replace-demo` 없이 적재한다. 이 옵션은 가상 장소를 비활성화해 이용권을 발급할 수 없게 하므로, 혜택 흐름을 시연할 수 있도록 가상 장소를 유지한다. 실제 관광지는 검토 대기 상태이므로 [승인](data-collection/tourism-data.md#적재) 전까지 추천에는 가상 장소만 표시된다.

## 카카오 로그인

기본 실행에서는 버튼이 준비 중으로 표시된다. 사용하려면 [카카오 로그인 설정](../README.md#카카오-로그인-설정)의 환경 변수를 저장소 루트의 `.env`에 작성하고 다시 실행한다. `.env`는 커밋하지 않는다.
