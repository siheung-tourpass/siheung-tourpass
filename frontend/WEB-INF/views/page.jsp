<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="root" value="${pageContext.request.contextPath}"/>
<c:set var="adminArea" value="${view eq 'place-feedback-statistics' ? 'statistics' : view eq 'passes' or view eq 'pass' or (view eq 'editor' and not semantic) ? 'benefits' : view eq 'admin' or view eq 'relations' or view eq 'editor' or view eq 'goods' ? 'tourism' : ''}"/>
<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><title>시흥 투어패스</title><link rel="icon" type="image/png" href="${root}/assets/tourpass-icon.png"><link rel="apple-touch-icon" href="${root}/assets/tourpass-icon.png"><link rel="stylesheet" href="${root}/assets/style.css"><link rel="stylesheet" href="${root}/assets/admin.css"><script src="${root}/assets/site.js" defer></script><script src="${root}/assets/admin.js" defer></script></head><body class="page-${fn:escapeXml(view)}"><a class="skip-link" href="#main">본문으로 건너뛰기</a>
<c:set var="headerBackPath" value=""/>
<c:choose>
<c:when test="${view eq 'goods'}"><c:set var="headerBackPath" value="/admin"/></c:when>
<c:when test="${view eq 'journey-photocards'}"><c:set var="headerBackPath" value="/journeys/detail?id=${data.id}"/></c:when>
<c:when test="${view eq 'journey'}"><c:set var="headerBackPath" value="/journeys?status=${data.status}"/></c:when>
<c:when test="${view eq 'journey-builder'}"><c:set var="headerBackPath" value="/journeys"/></c:when>
<c:when test="${view eq 'product' or view eq 'merchant'}"><c:set var="headerBackPath" value="/merchants"/></c:when>
<c:when test="${view eq 'place'}"><c:set var="headerBackPath" value="/explore"/></c:when>
<c:when test="${view eq 'pass'}"><c:set var="headerBackPath" value="${user.role eq 'ADMIN' ? '/admin/passes' : '/passes'}"/></c:when>
</c:choose>
<header class="site-header"><div class="header-inner"><div class="header-leading"><c:if test="${not empty headerBackPath}"><a class="header-back" href="${root}${fn:escapeXml(headerBackPath)}" aria-label="목록으로 돌아가기"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6"/></svg></a></c:if></div><a class="brand" href="${root}/"><img class="brand-mark" src="${root}/assets/tourpass-icon.png" width="24" height="24" alt="">시흥 투어패스</a><div aria-hidden="true"></div></div></header>
<main id="main" tabindex="-1">
<c:choose>
<c:when test="${view eq 'landing'}"><%@ include file="landing.jspf" %></c:when>
<c:when test="${view eq 'home' and user.role eq 'ADMIN'}"><%@ include file="admin-home.jspf" %></c:when>
<c:when test="${view eq 'home'}">
<div class="home-layout home-dashboard">
<header class="home-heading"><h1>나만의 시흥 여행을 담다</h1><p>새로운 여행을 찾고, 준비하던 여행을 이어가세요.</p></header>
<div class="home-photo"><c:set var="imagePlace" value="${homePhoto}"/><%@ include file="place-image.jspf" %></div>
<div class="home-card-grid">
<c:if test="${user.role eq 'VISITOR'}"><%@ include file="journey-start-picker.jspf" %></c:if>
<a class="home-card home-start" href="${root}${user.role eq 'VISITOR' ? '/journeys' : '/explore'}" ${user.role eq 'VISITOR' ? 'data-popover-open="journey-start-picker" aria-haspopup="dialog"' : ''}><div><span class="home-card-label">장소 · 행사 · 음식점</span><h2>새 여행 만들기</h2><p>마음에 드는 카드를 골라<br>나만의 여행을 시작하세요.</p></div><span class="home-card-action">카드 둘러보기 <span aria-hidden="true">↗</span></span></a>
<c:if test="${user.role eq 'VISITOR'}">
<a class="home-card home-drafts" href="${root}/journeys"><div><h2>만들던 여행</h2><strong class="home-card-count">${homeDraftCount}<span>개 제작 중</span></strong><p class="home-draft-name"><c:out value="${empty homeLatestDraft ? '아직 제작 중인 여행이 없어요.' : homeLatestDraft.name}"/></p></div><span class="home-card-action">${empty homeLatestDraft ? '제작 중 목록' : '이어 만들기'} <span aria-hidden="true">→</span></span></a>
<a class="home-card home-passes" href="${root}/journeys?status=CONFIRMED"><div><h2>내 패스</h2><strong class="home-card-count">${homePassCount}<span>개 제작 확정</span></strong><p>방문 날짜와 메모를 남겨 보세요.</p></div><span class="home-card-action">패스 보기 <span aria-hidden="true">→</span></span></a>
</c:if>
</div>
<p class="home-benefits"><a href="${root}/merchants">여행 혜택 살펴보기 →</a> · <a href="${root}/passes">내 혜택 이용권</a></p>
<details class="home-help"><summary>이용 방법</summary><ol><li>관심 있는 관광지와 함께 갈 행사·음식점·카페를 골라 여행에 담으세요.</li><li>상세에서 이용 조건을 비교하고 순서와 이름을 정해 저장하세요.</li><li>확정한 여행은 내 패스에서 확인하고 기억에 남는 경험을 기록하세요.</li></ol><p>여행 패스는 만료되지 않아요. 사용 완료한 여행은 여행 기록에서 다시 꺼내 볼 수 있어요.</p></details>
</div>
</c:when>
<c:when test="${view eq 'place-feedback-statistics'}"><%@ include file="place-feedback-statistics.jspf" %></c:when>
<c:when test="${view eq 'public-card'}"><%@ include file="public-card.jspf" %></c:when>
<c:when test="${view eq 'profile'}"><%@ include file="profile.jspf" %></c:when>
<c:when test="${view eq 'journey-builder'}"><%@ include file="journey-builder.jspf" %></c:when>
<c:when test="${view eq 'journey-photocards'}"><%@ include file="journey-photocards.jspf" %></c:when>
<c:when test="${view eq 'journeys' or view eq 'journey'}"><%@ include file="journeys.jspf" %></c:when>
<c:when test="${view eq 'error'}"><h1>요청 확인</h1><p class="error" role="alert"><c:out value="${message}"/></p><p>오류 코드: <c:out value="${errorCode}"/></p><a href="${root}/">처음으로</a></c:when>
<c:when test="${view eq 'login'}"><%@ include file="login.jspf" %></c:when>
<c:when test="${view eq 'catalog' or view eq 'product' or view eq 'merchant' or view eq 'place'}"><%@ include file="catalog.jspf" %></c:when>
<c:when test="${view eq 'recommendations'}"><%@ include file="recommendations.jspf" %></c:when>
<c:when test="${view eq 'passes' or view eq 'pass'}"><%@ include file="passes.jspf" %></c:when>
<c:when test="${view eq 'redeem' or view eq 'confirm' or view eq 'history' or view eq 'feedback'}"><%@ include file="usage.jspf" %></c:when>
<c:when test="${view eq 'admin' or view eq 'editor' or view eq 'relations' or view eq 'goods'}"><%@ include file="admin.jspf" %></c:when>
</c:choose></main><c:if test="${view ne 'landing'}"><footer class="site-footer">수업용 시연 서비스 · 화면 시각은 한국 시간 · 실제 관광 자료의 영업·예약·이동 가능성은 미확인</footer></c:if><c:if test="${view ne 'landing' and view ne 'login'}"><nav class="bottom-nav" aria-label="주 메뉴"><div class="bottom-nav-inner">
<a href="${root}/" ${view eq 'home' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7v11h-6v-7H9v7H3V10Z"/></svg><span>홈</span></a>
<c:if test="${empty user or user.role eq 'VISITOR'}"><a href="${root}/journeys" ${journeyStatus eq 'PLANNING' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 6V4h8v2M5 6h14v15H5V6Zm4 4v7m6-7v7"/></svg><span>여행 만들기</span></a>
<a href="${root}/journeys?status=CONFIRMED" ${journeyStatus eq 'CONFIRMED' or view eq 'passes' or view eq 'pass' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4V6Zm12 2v2m0 2v2m0 2v1"/></svg><span>내 패스</span></a>
<a href="${root}/journeys?status=ARCHIVED" ${journeyStatus eq 'ARCHIVED' or view eq 'history' or view eq 'feedback' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 5h16v16H4V5Zm4-2v4m8-4v4M8 11h8m-8 4h5"/></svg><span>여행 기록</span></a>
</c:if>
<c:if test="${user.role eq 'STAFF'}"><a href="${root}/merchant/redeem" ${view eq 'redeem' or view eq 'confirm' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg><span>혜택 사용</span></a>
<a href="${root}/merchant/history" ${view eq 'history' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 5h16v16H4V5Zm4-2v4m8-4v4M8 11h8m-8 4h5"/></svg><span>가맹점 이력</span></a></c:if>
<c:if test="${user.role eq 'ADMIN'}">
<a href="${root}/admin" ${adminArea eq 'tourism' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Zm6-3v15m6-12v15"/></svg><span>관광 관리</span></a>
<a href="${root}/admin/catalog?collection=products" ${adminArea eq 'benefits' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4V6Zm12 2v2m0 2v2m0 2v1"/></svg><span>혜택 관리</span></a>
<a href="${root}/admin/place-feedback" ${adminArea eq 'statistics' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 3v18h17M8 16v-5m5 5V7m5 9V4"/></svg><span>통계</span></a>
</c:if>
<a href="${root}/profile" ${view eq 'profile' or view eq 'login' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a7 7 0 0 0-14 0v2m7-9a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z"/></svg><span>프로필</span></a>
</div></nav></c:if></body></html>
