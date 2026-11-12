# 관광 데이터 — 시흥 투어패스

[문서 안내](../../README.md#관련-문서) · [데이터 수집 공통 기준](common.md)

## 원본

- [siheung-tourism.json](../../backend/data/siheung-tourism.json)의 `sources`·`source_ids`(`checked_on`·`content_date`)에서 출처·근거를 관리한다.
- 사실 요약(`SOURCE_FACT`)·서비스 분류(`PROJECT_CLASSIFICATION`)·자체 개념(`PROJECT_MODEL`), 실제 자료(`REAL`)·자체 테마 부모(`DEMO`·`PROJECT_MODEL`)를 구분한다.
- 장소당 검색 지역 1개(`region_code` = `locatedIn`, `전체` 미저장, 시연 ID 분리)를 부여한다.

## 적재

### 최초 적재

- 초기 적재·마이그레이션은 [README](../../README.md#시작하기)를 따른다(기존 시연 DB는 백업 후 관광 SQL 생성·적재 명령만 1회 실행).
- 장소·관계는 `DRAFT`(검토자·시각 `null`)로 적재되며, 관리자가 각각 승인하기 전에는 추천에서 제외한다(수정 시 재검토).

### 추가 적재·시연 자료 전환

- 신규 장소 추가 시 `python3 backend/scripts/tourism-sql.py --place-code 장소코드`를 사용한다(기적재 코드 중복 시 실패).
- `--replace-demo`는 기존 기록을 유지한 채 가상 장소·빈 지역만 비활성화한다(테스트 DB는 미사용). 상세 주소가 없으면 `상세 주소 미확인`으로 표시한다.

## 검토

- 과거 일정·주소 불일치·미확인 조건은 [수집 자료](../../backend/data/siheung-tourism.json) 근거에서 확인하며 현재 운영 조건으로 추정하지 않는다.

### 방문 조건

- 공식 운영기관 자료로 운영시간·이용료·예약·주차·야간 개방·아동 동반 조건을 확인해 출처·확인일과 함께 반영하고, 미확인 항목은 방문 전 확인을 안내한다(문제 발굴 게시글은 조사 질문 출처로만 사용).

## 이미지

[공통 이미지 기준](common.md#이미지)에 따라 [이미지 메타데이터](../../backend/src/main/resources/place-images.json)와 [정적 자산](../../frontend/assets/places)(Wikimedia Commons 및 공공누리 제1유형 공개 미리보기)을 배포한다. 상세는 원본 비율로 축소하고 카드 배경·표지는 영역에 맞춰 크롭·틴트를 적용한다.
