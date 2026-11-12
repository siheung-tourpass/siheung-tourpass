# 지역 문제와 사용자 검증 — 시흥 투어패스

[문서 안내](../../README.md#관련-문서) · [남은 작업](../03-tech/01-architecture.md#남은-작업과-리스크) · [최종 발표 준비 기준](final-presentation.md)

## 기획 근거

[한국공학대학교 문제 발굴 게시판](https://startup.tukorea.ac.kr/exploration/problemSearch/problemDiscoveryList.hs) 제안을 문제 가설로 활용한다(검증된 통계·현황이 아니며 장소 반영은 [관광 데이터 검토](../data-collection/tourism-data.md#검토), 효과 표현은 [공통 요구사항](../01-func/01-common.md#범위) 적용).

| 문제 가설·출처 | 활용할 기능·안내 |
| --- | --- |
| 갯골 방문이 단발 산책에 머물고 체험 연결이 부족함 ([체류·재방문](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=189) · [생태·역사](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=581)) | 관광지 선택 후 행사·음식점/카페 통합 여행 구성 및 추천 근거 안내 |
| 여행 후 회상·전달할 콘텐츠 부족 ([관광 경험·인지도](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=568)) | 여행 방문 기록·아카이브 및 사진·안내 QR 포토카드 |
| 자연환경과 지역 상권 연결 부족 ([은계호수](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=199) · [목감](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=561)) | 관광지·공식 지정 업소 통합 탐색 및 직선거리순 후보 비교 |
| 가족 방문객의 아동 활동 탐색 어려움 ([은계 아동 활동](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=198)) | 동반 유형·추천 이유·행사 날짜 탐색 및 연령·보호자 조건 확인 안내 |
| 방문 전 주차·야간 여건 확인 필요 ([물왕호수 주차](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=188) · [오이도 야간](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=193)) | 실제 관광지 상세의 방문 전 확인 안내 및 공식 자료 검토 항목 |
| 현장 권유 대신 사전 이용 조건 비교 필요 ([오이도 관광 경험](https://startup.tukorea.ac.kr/exploration/problemSearch/problemSearchDetail.hs?problemSeq=184)) | 비용·예약·출처 표시 및 공식 지정과 혜택 제휴 구분 |

## 검증 절차

1. 방문객 3명 이상·가맹점 담당자 2명 이상을 확보한다(관리자 출력은 준비자 수행).
2. 개별 안내 화면과 패스 화면을 교차 제시하며 역할별 과제를 수행한다.
3. [검증 기록](#검증-기록)을 남기고 중대 오해는 수정 후 재검증한다.

## 검증 과제

| 역할 | 과제·연결 문제 | 관찰·질문 |
| --- | --- | --- |
| 방문객 | 관광지 시작 여행 구성 — 체험 연결 | 생성·추천 담기·순서 변경 수행, 새로 발견한 후보와 추가 선택 기록 |
| 방문객 | 행사·음식점/카페 비교 — 상권 연결 | 위치·출처 확인, 직선거리를 실제 이동거리·시간으로 오해하는지 확인 |
| 방문객 | 아이 동반 후보 탐색 — 아동 활동 | 동반 유형·추천 이유·날짜 탐색, 추천을 안전·접수 보장으로 오해하는지 확인 |
| 방문객 | 주차·야간 조건 확인 — 사전 확인 | 출처·확인일·미확인 구분 및 정보 없음과 이용 불가 구분 확인 |
| 방문객 | 음식점 안내·혜택 비교 — 조건 비교 | 비용·예약 확인, 공식 지정 업소를 자동 할인 제휴로 오해하는지 확인 |
| 방문객·담당자 | 혜택 제시·사용·결과 확인 — 조건 이해 | 코드 제시·사용 처리 후 여행 패스와 혜택 이용권 차이 이해 확인 |
| 방문객 | 방문 기록 저장·재조회 — 경험 회상 | 아카이브 재조회 및 여행 기록과 혜택 피드백 차이 이해 확인 |
| 방문객 | 출력 카드 QR 조회 — 회상·전달 | 개인정보 노출 없이 공개 안내·출처 및 다른 카드 탐색 연결 확인 |

## 검증 기록

| 구분 | 기록할 내용 |
| --- | --- |
| 수행 조건 | 익명 번호·역할·시나리오·제시 화면/순서(실제 가족 여부 구분) |
| 과제 결과 | 완료 여부·소요 시간·도움 요청·오해·관찰 내용 |
| 수정·재검증 | 중대 오해 수정 내역 및 재검증 결과 |
| 주관적 응답 | 재이용 의향·자유 의견(관찰 결과와 분리) |

## 검증 한계

추가 카드 선택과 QR 접속은 화면 관찰 결과이며 실제 방문·체류시간·매출 증가의 증거로 해석하지 않는다([공통 요구사항](../01-func/01-common.md#범위)).
