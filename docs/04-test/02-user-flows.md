# 핵심 유저 플로우 — 화면 캡처와 UX 검토

[요구사항](../01-func/README.md) · [디자인 기준](../02-uxui/01-design.md)

2026-11-11 로컬 Chrome에서 실제 관광·가상 혜택 자료를 적재한 검증 DB로 촬영한 25장(모바일 `390×844`, 관리자 데스크톱 `1280×900`, 연한 초록 블록은 코드·QR 마스킹)이다.

## 검토 후 반영

| 발견한 문제 | 반영한 UX/UI |
| --- | --- |
| 진행 중 여행을 한 번 누르면 즉시 완료됨 | 확인 팝오버(이름·변경/환불 제한 안내, 닫기·Esc 취소) 거친 뒤 완료 |
| 구매 직전 추가 부담·예약 조건 비교가 어려움 | 장소별 혜택 아래 본인 부담액·예약 조건 표시 |
| 비스크립트 환경에서도 덱 전환 대기로 화면이 숨겨짐 | 스크립트 활성 환경에서만 초기 대기 적용 |
| 숨긴 별점·사진 입력에 일반 입력 너비가 적용됨 | 공통 입력 스타일에서 접근성 숨김 입력 제외 |
| 위치 해시 없이 기록 화면 진입 시 JS 오류 발생 | 장소 ID 기반 펼침 및 빈 위치 예외 처리 |
| 미래 방문일을 서버 제출 후에야 거절함 | KST 오늘까지로 날짜 입력 제한(서버 검증 유지) |
| 담당자 사용 처리 후 다음 조회가 하단 메뉴에만 있음 | 사용 이력 상단에 `다음 이용권 코드 조회` 추가 |
| 피드백 뒤로가기가 내 투어패스로 이동함 | 혜택 사용 이력으로 복귀 경로 수정 |
| 홈·이용 방법의 기록 안내가 실제 순서와 다름 | 진행 중 코드 확인 → 완료 후 기록 순서로 문구 정리 |

## 1. 탐색·로그인·여행 구성

| 진입·탐색 | 로그인·상품 선택 |
| --- | --- |
| [![서비스 소개](../assets/user-flows/01-landing.png)](../assets/user-flows/01-landing.png)<br>소개에서 공개 탐색·로그인으로 진입한다. | [![로그인](../assets/user-flows/03-login.png)](../assets/user-flows/03-login.png)<br>방문객 계정으로 로그인한다. |
| [![전체 상품](../assets/user-flows/04-products.png)](../assets/user-flows/04-products.png)<br>포함 장소를 비교하고 `이 상품으로 여행 시작`을 누른다. | [![여행 덱 구성](../assets/user-flows/05-builder.png)](../assets/user-flows/05-builder.png)<br>포함 장소가 담긴 상태에서 카드를 더하거나 뺀다. |
| [![담은 카드와 지도](../assets/user-flows/06-selected-map.png)](../assets/user-flows/06-selected-map.png)<br>하단 시트를 펼쳐 위치·순서를 확인한다. | [![여행 확인](../assets/user-flows/07-review.png)](../assets/user-flows/07-review.png)<br>담은 카드의 지도와 방문 순서를 확인한다. |

| 여행 정보·저장 선택 | 다음 행동 |
| --- | --- |
| [![여행 이름·날짜와 저장 버튼](../assets/user-flows/07b-review-actions.png)](../assets/user-flows/07b-review-actions.png) | 이름·날짜를 정하고 임시 저장·구매 없이 확정·확정 후 구매를 선택한다. |

## 2. 구매·코드 제시·담당자 혜택 사용

| 구매·코드 제시 | 담당자 확인·사용 결과 |
| --- | --- |
| [![구매 전 확인](../assets/user-flows/08-checkout.png)](../assets/user-flows/08-checkout.png)<br>포함 장소의 본인 부담·예약 조건을 확인한다. | [![결제 금액과 결제 버튼](../assets/user-flows/08b-checkout-payment.png)](../assets/user-flows/08b-checkout-payment.png)<br>최종 금액을 확인하고 결제한다. |
| [![여행 상세 코드와 혜택](../assets/user-flows/09-pass-code.png)](../assets/user-flows/09-pass-code.png)<br>구매한 여행의 코드·QR을 제시한다. | [![담당자 코드 조회](../assets/user-flows/10-staff-lookup.png)](../assets/user-flows/10-staff-lookup.png)<br>배정 가맹점을 고르고 코드를 입력한다. |
| [![혜택 사용 전 확인](../assets/user-flows/11-staff-confirm.png)](../assets/user-flows/11-staff-confirm.png)<br>미차감 상태에서 잔여·가능 여부를 확인하고 사용 처리한다. | [![담당자 사용 이력](../assets/user-flows/12-staff-history.png)](../assets/user-flows/12-staff-history.png)<br>사용 이력을 확인하고 다음 코드 조회로 이동한다. |
| [![혜택 피드백](../assets/user-flows/13-feedback.png)](../assets/user-flows/13-feedback.png)<br>사용 이력에서 평가를 남긴다. | 뒤로가기 시 혜택 사용 이력으로 돌아간다. |

## 3. 여행 완료·방문 기록·포토카드

| 완료 전 확인 | 완료 후 기록 |
| --- | --- |
| [![여행 완료 확인](../assets/user-flows/14-finish-confirm.png)](../assets/user-flows/14-finish-confirm.png)<br>변경·환불 제한을 확인하고 완료한다. | [![여행 방문 기록](../assets/user-flows/15-travel-record.png)](../assets/user-flows/15-travel-record.png)<br>오늘까지의 방문 날짜·별점·메모를 입력한다. |
| [![저장된 방문 기록](../assets/user-flows/15b-record-saved.png)](../assets/user-flows/15b-record-saved.png)<br>저장된 방문 날짜·별점·메모를 확인한다. | 기록 수정에서 다시 편집할 수 있다. |
| [![사진 없는 포토카드](../assets/user-flows/16-photocard-empty.png)](../assets/user-flows/16-photocard-empty.png)<br>사진이 없으면 기록 업로드로 안내한다. | 사진 업로드·교체·삭제·권한은 [브라우저 검사](../../backend/tests/browser/journey_photos_check.cjs)로 검증한다. |

## 4. 관리자 관광 카드 출력

| 대상·설정 | 출력 미리보기 |
| --- | --- |
| [![관리자 관광 카드 목록](../assets/user-flows/17-admin-catalog.png)](../assets/user-flows/17-admin-catalog.png)<br>종류·검색 조건으로 카드를 고른다. | [![관리자 출력 설정](../assets/user-flows/18-admin-print-settings.png)](../assets/user-flows/18-admin-print-settings.png)<br>선택한 카드와 출력 조건을 설정한다. |
| [![모바일 출력 미리보기](../assets/user-flows/19-admin-print-preview.png)](../assets/user-flows/19-admin-print-preview.png)<br>모바일 미리보기에서 출력 형태를 확인한다. | [![데스크톱 출력 미리보기](../assets/user-flows/20-admin-desktop.png)](../assets/user-flows/20-admin-desktop.png)<br>데스크톱에서도 동일한 미리보기를 제공한다. |

## 5. 빈 결과·오류 복구

| 상황 | 다음 행동 |
| --- | --- |
| [![검색 결과 없음](../assets/user-flows/02-empty-search.png)](../assets/user-flows/02-empty-search.png) | `전체 카드 보기`로 검색 조건을 초기화한다. |
| [![이용 기간 충돌](../assets/user-flows/21-checkout-conflict.png)](../assets/user-flows/21-checkout-conflict.png) | 결제 차단 사유와 `내 투어패스 확인`·여행 복귀 링크를 제공한다. |
| [![여행을 찾을 수 없음](../assets/user-flows/22-missing-trip.png)](../assets/user-flows/22-missing-trip.png) | 여행 목록으로 이동해 다시 찾는다. |

## 검증·재촬영

- [흐름 검사](../../backend/tests/browser/user_flows_check.cjs)는 새 검증 DB에서 전체 플로우를 검증·캡처한다([README 테스트](../../README.md#테스트)).
- Maven 패키징, 계층 경계·단위 검사, 핵심 흐름, UX 복구·카드(비스크립트 포함)·관리자 출력 검사가 통과했다. 참여자 검증은 [사용자 검증 절차](../presentation/project-validation.md#검증-절차)를 따른다.
