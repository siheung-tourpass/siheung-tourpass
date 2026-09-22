# 음식점·카페 이미지 조사

확인일: 2026-10-18. [수집 자료](../backend/data/siheung-dining.json)의 사진 후보 조사 근거와 미확보 대상의 재개 조건을 기록한다. 등록 사진·메타데이터는 수집 자료에서, 채택·표시 정책은 [음식점 이미지 기준](dining-data.md#이미지)에서 관리한다.

## 후보별 검토

| 업소 | 후보 출처 | 확인 상태·남은 확인 |
| --- | --- | --- |
| 더베이징 | [한국농어촌공사 웰촌](https://www.welchon.com/web/lay1/program/S1T11C446/travelPlaceInfo/view.do?api_type=1&cont_seq=2838256) · [한국관광공사](https://korean.visitkorea.or.kr/detail/ms_detail.do?cotid=7e30cb25-59bc-4b3f-8978-9e627f96c29c) | **등록**: 중심상가1길 14 주소·간판과 개별 `cpyrhtDivCd=Type3` 대조. 채택 파일·출처·권리 표기는 수집 자료의 `image` 참조. |
| 보리굴비마을 | [다이닝코드](https://www.diningcode.com/profile.php?rid=jyWue622USYz) · [한국관광공사](https://korean.visitkorea.or.kr/detail/ms_detail.do?cotid=42c4554d-0a1b-44f6-8389-0dbfe4a70bde) | **보류**: 콘텐츠랩 2635044의 개별 사진 2635275·2635279·2635281은 제3유형이나 사진명이 `도토리마을묵집`이다. 업소명 불일치로 제외; 현재 업소 촬영 근거 필요. |
| 땅끝횟집 | [오이도 업소 소개](https://conyplace.com/%EB%95%85%EB%81%9D%ED%9A%9F%EC%A7%91-%EC%98%A4%EC%9D%B4%EB%8F%84%EC%9D%98-%ED%95%B4%EC%82%B0%EB%AC%BC-%EC%A0%84%EB%AC%B8-%EC%8B%9D%EB%8B%B9/) | 페이지는 정왕동 1973-20·031-431-2657로 오이도 업소와 일치한다. 이미지 참조는 있으나 촬영 대상·원촬영자·재사용 조건 미확인. 해남 동명 업소 검색 결과는 제외한다. |
| 오이도풍차횟집 | [다이닝코드 풍차횟집](https://www.diningcode.com/profile.php?rid=duuZvKhN2jGL) | 오이도로 143 주소가 일치하며 대표·이용자 사진 후보가 있다. 촬영자별 허락·매장 제공 원본 여부 확인 필요. |
| 진애연(주) | [당근 업소 페이지](https://www.daangn.com/kr/local-profile/%EC%A7%84%EC%95%A0%EC%97%B0-gx2me5mzqdzv/) · [다이닝코드](https://www.diningcode.com/profile.php?rid=V273f7kjvcjG) | 업소 페이지에 소개 사진 10개가 표시된다. 업소 직접 제공 여부와 사진 권리자·허락 확인 필요. |
| 폴 바셋 신세계시흥프리미엄아울렛점 | [다이닝코드](https://www.diningcode.com/profile.php?rid=gaUDVaDVSnP1) · [테이블링](https://www.tabling.co.kr/place/677cd11766de5f06988746ef) | 시흥 아울렛 지점의 대표·이용자 사진 후보가 있다. 촬영자·브랜드 제공 이미지 사용 조건 확인 필요. 시흥 배곧점은 다른 지점이므로 제외한다. |
| 스타벅스시흥대야동점 | [다이닝코드](https://www.diningcode.com/profile.php?rid=M89sUfso5QcF) | 해당 지점의 대표·이용자 사진 후보가 있다. 촬영자·브랜드 제공 원본의 사용 조건 확인 필요. |

## 조사 근거·재개 조건

- 더베이징의 음식 대표 사진 대신 간판을 직접 대조할 수 있는 외관 사진을 채택했다. [사진 원본](https://tong.visitkorea.or.kr/cms/resource/45/2838245_image2_1.jpeg) · [콘텐츠랩 상세](https://api.visitkorea.or.kr/#/hubTourSearchCatDetail?contentId=2838256&langDiv=KOR). 촬영연도는 미확인이다.
- 콘텐츠랩 공개 `hub/getTourDbInfo.do` 검색과 `com/getImage.do`의 개별 사진 이용 조건을 대조했다. 땅끝횟집·풍차횟집·진애연은 검색 결과가 없고 폴바셋·스타벅스 검색에서 등록된 시흥 지점 사진을 찾지 못했다. 검색 결과 부재는 사진이나 업소가 존재하지 않는다는 뜻이 아니다.
- 나머지는 업소가 권리를 보유한 외관·실내 원본과 서비스 게시·편집 허락을 확보하면 검토를 재개한다. 이번 조사에서 업소·촬영자에게 연락하지 않았다.
- [공유마당 보리굴비 사진](https://gongu.copyright.or.kr/gongu/wrt/wrt/view.do?menuNo=200018&wrtSn=13276201)은 검색 결과에 CC BY로 표시되지만 보리굴비마을 촬영 근거를 확인하지 못해 해당 업소 카드 후보에서 제외했다.
