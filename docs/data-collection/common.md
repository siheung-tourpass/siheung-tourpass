# 데이터 수집 공통 기준 — 시흥 투어패스

[문서 안내](../../README.md#관련-문서) · [관광](tourism-data.md) · [행사](events-data.md) · [음식점](dining-data.md) · [음식점 이미지 조사](dining-images.md)

## 원본

- 출처 URL·원문 기준일(`content_date`/`contentDate`)·확인일(`checked_on`/`checkedOn`)·근거는 각 수집 JSON에서만 관리한다(기준일 미확인 시 생략).
- 원문 사실과 서비스 분류를 구분하며, 부분 시설·프로그램 근거를 전체 장소·시흥시 특성으로 확대하지 않는다. 근거 없는 값과 검토자·승인 시각은 `null`·`UNKNOWN`으로 둔다.

## 좌표

WGS84 위도·경도·출처·확인일·대표 지점 근거를 JSON에 기록하고 WAR에 포함해 [CardCoordinates](../../backend/src/main/java/kr/ac/siheung/tourpass/service/CardCoordinates.java)가 코드로 연결한다(한국관광공사·카카오맵 대조, 허구·비공개 카드는 실좌표 제외).

## 이미지

- 촬영 대상과 재사용 조건이 확인된 사진만 등록하고 원본·대체 텍스트·저작물명·제공기관·촬영자·출처·라이선스·확인일·근거를 보존한다(제3자 사진 제외·텍스트 한정 자료 및 대체/생성 이미지 사용 금지).
- 화면에 촬영자·날짜·출처·라이선스·편집 여부를 표시하며, 미확보 시 추천·상세는 준비 중으로, 탐색 카드는 [카드 정보·사진](../02-uxui/01-design.md#카드-정보사진) 기준으로 표시한다.
- 변경 금지 사진은 크롭·틴트·문구 중첩 없이 원본 비율 전체를 표시한다([관광 사진](tourism-data.md#이미지) · [행사 이미지](events-data.md#이미지) · [음식점 이미지](dining-data.md#이미지)).
