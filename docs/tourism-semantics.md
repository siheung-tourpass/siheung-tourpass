# 관광 추천 의미 모델

[요구사항](../spec.md)의 기능 8·10~13을 구현한다. 실제 지역 소개와 허구의 패스 혜택을 구분하며 추천은 영업·예약·이동 가능성을 보장하지 않는다.

추천 결과와 장소 상세에서 구성 중인 본인 여행 패스로 담는다. 장소 목록·기록의 원본은 [JourneyService](../backend/src/main/java/kr/ac/siheung/tourpass/service/JourneyService.java)다. 일반 장소의 직접 방문 기록·만족도는 개인 아카이브용이며 추천 점수나 인증된 혜택 피드백에 합산하지 않는다.

## 세 계층의 역할

- 어노테이션: 장소·관계의 실제/시연 구분, 출처·확인일·근거·검토 상태.
- 온톨로지: 장소·지역·테마·동반 유형과 관계의 타입·상위 테마 추론.
- 시맨틱 레이어: 승인된 관계를 해석해 지역 필터·순위·추천 이유·개인 선호를 제공.

지역은 서비스 분류이며 행정구역을 뜻하지 않는다. 관계 부재는 정보 없음이다. 테마만으로 동반 적합성·안전성·가격·예약 가능성을 추론하지 않는다. 선택 사유·자유 의견은 객관적 장소 속성으로 바꾸지 않는다.

## 온톨로지

개념·관계·자료 필드·외래 키·유일 제약은 [스키마](../backend/sql/schema.sql), A~D와 초기 관계는 [시연 자료](../backend/sql/seed.sql)가 원본이다. 관계 타입·유형 검사·순환 검사·활성/승인 경로·설명 경로 선택·검토 규칙은 [OntologyService](../backend/src/main/java/kr/ac/siheung/tourpass/service/OntologyService.java)에 둔다.

## 시맨틱 레이어

지역은 필터, 테마·동반 유형은 순위 기준이다. 기본 점수는 선택 테마 일치당 2점 + 동반 일치 1점. 개인 보정은 유효 테마가 겹치는 본인 현재 평가의 `(만족도−3)` 평균이며 평가당 한 번 계산한다. 합계 내림차순·장소 ID 오름차순으로 최대 3개다.

후보 제외·추론 일치·분수 정렬·추천 이유·현재 패스 사용 가능 판정은 [RecommendationService](../backend/src/main/java/kr/ac/siheung/tourpass/service/RecommendationService.java), 소유 성공 이력의 평가·최초 테마 보존·수정/삭제는 [FeedbackService](../backend/src/main/java/kr/ac/siheung/tourpass/service/FeedbackService.java)가 원본이다. 화면은 [추천 JSP](../frontend/WEB-INF/views/recommendations.jspf)와 [사용/평가 JSP](../frontend/WEB-INF/views/usage.jspf)에 구현했다.

## 검증 예시

기대값 표를 문서에 복제하지 않는다. [API 검사](../backend/tests/api_check.py)의 지역·상위 테마·순위·평가 변경, [관리 검사](../backend/tests/admin_check.py)의 DRAFT·관계 타입·순환·과거 조건 보존, [재시작 검사](../backend/tests/restart_check.py), [화면 검사](../backend/tests/web_check.py)를 실행한다. 명령은 [README](../README.md#테스트)에 둔다.

실제 관광 자료는 [수집 안내](tourism-data.md)와 [JSON 원본](../backend/data/siheung-tourism.json)에 별도로 둔다. [변환 스크립트](../backend/scripts/tourism-sql.py)는 DRAFT로 적재하며 관리자가 장소와 관계를 각각 승인하기 전에는 추천에 사용하지 않는다.
