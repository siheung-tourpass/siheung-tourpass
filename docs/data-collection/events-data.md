# 행사 데이터 — 시흥 투어패스

[문서 안내](../../README.md#관련-문서) · [데이터 수집 공통 기준](common.md)

[행사 데이터](../../backend/data/siheung-events.json)에 가을 행사 표본의 공식 일정·장소·비용·예약·출처·이미지 메타데이터를 관리한다.

## 원본·검토

- 공개·담기 정책과 화면 날짜 분류는 [방문객 기능 9](../01-func/02-visitor.md#기능), 미확인 값 처리는 [공통 원본 기준](common.md#원본)을 따른다(`date_position_on_checked_on`은 확인일 기준 분류).
- 검토 대기 행사·장소 연결(`PROJECT_CLASSIFICATION`: 동일 장소 `VENUE`, 관련 지역 `RELATED_AREA`)·이미지는 `DRAFT`로 둔다.

## 이미지

- [공통 이미지 기준](common.md#이미지)에 따라 [원본 이미지](../../backend/data/event-images)를 무변환 보존하고 SHA-256·라이선스(`PAGE_LICENSE_CHECKED`, 경기도청 공공누리 제3유형·시흥시 공식 블로그 CC BY-NC-ND 2.0 KR 비영리 한정)가 확인된 로컬 파일만 배포한다.
- 이용 조건 미확인 홍보물은 URL만 기록하고 노출을 보류하며, 공통·부대행사 이미지는 관계를 명시한다.

## 배포

JSON과 [원본 이미지](../../backend/data/event-images)는 WAR에 포함되며 변경 시 재빌드한다([시작하기](../../README.md#시작하기)).
