# 추천 모델

## 계층

- 어노테이션: 장소·관계의 실제/시연 구분·출처·확인일·근거·검토 상태.
- 온톨로지: 장소·지역·테마·동반 유형의 관계 타입과 상위 테마 추론.
- 시맨틱 레이어: 승인된 관계의 필터·순위·추천 이유·개인 보정.

관계 부재는 정보 없음이다. 테마만으로 동반 적합성·안전성·가격·예약을 추론하지 않는다.

## 계산

기본 점수 = 선택 테마 일치당 2점 + 동반 일치 1점. 개인 보정 = [기능 12](../spec.md#기능)에 해당하는 평가의 `(만족도−3)` 평균. 합계 내림차순·장소 ID 오름차순으로 정렬한다.

## 구현 원본

| 원본 | 책임 |
| --- | --- |
| [OntologyService](../backend/src/main/java/kr/ac/siheung/tourpass/service/OntologyService.java) | 타입·순환·승인 경로·추론·검토 |
| [RecommendationService](../backend/src/main/java/kr/ac/siheung/tourpass/service/RecommendationService.java) | 후보 제외·분수 정렬·이유·이용권 사용 가능 판정 |
| [FeedbackService](../backend/src/main/java/kr/ac/siheung/tourpass/service/FeedbackService.java) | 성공 이력 소유권·최초 테마 보존·평가 변경 |
