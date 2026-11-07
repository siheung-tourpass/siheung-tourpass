<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="ko_KR"/><c:set var="root" value="${pageContext.request.contextPath}"/>
<c:set var="adminArea" value="${view eq 'place-feedback-statistics' ? 'statistics' : view eq 'passes' or view eq 'pass' or (view eq 'editor' and not semantic) ? 'benefits' : view eq 'admin' or view eq 'relations' or view eq 'editor' or view eq 'goods' ? 'tourism' : ''}"/>
<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><title>시흥 투어패스</title><link rel="icon" type="image/png" href="${root}/assets/images/tourpass-icon.png"><link rel="apple-touch-icon" href="${root}/assets/images/tourpass-icon.png"><link rel="stylesheet" href="${root}/assets/css/style.css"><link rel="stylesheet" href="${root}/assets/css/admin.css"><script src="${root}/assets/js/site.js" defer></script><script src="${root}/assets/js/admin.js" defer></script></head><body class="page-${fn:escapeXml(view)}"><a class="skip-link" href="#main">본문으로 건너뛰기</a>
<c:set var="headerBackPath" value=""/>
<c:choose>
<c:when test="${view eq 'goods'}"><c:set var="headerBackPath" value="/admin"/></c:when>
<c:when test="${view eq 'journey-photocards'}"><c:set var="headerBackPath" value="/journeys/detail?id=${data.id}"/></c:when>
<c:when test="${view eq 'journey'}"><c:set var="headerBackPath" value="/journeys?status=${data.status}"/></c:when>
<c:when test="${view eq 'journey-builder'}"><c:set var="headerBackPath" value="/journeys"/></c:when>
<c:when test="${view eq 'product'}"><c:set var="headerBackPath" value="/merchants"/></c:when>
<c:when test="${view eq 'checkout'}"><c:set var="headerBackPath" value="${empty checkoutJourney ? '/products/detail?id='.concat(data.id) : '/journeys/detail?id='.concat(checkoutJourney.id)}"/></c:when>
<c:when test="${view eq 'merchant'}"><c:set var="headerBackPath" value="/merchants?tab=facilities"/></c:when>
<c:when test="${view eq 'catalog' and guideTab eq 'facilities'}"><c:set var="headerBackPath" value="/merchants"/></c:when>
<c:when test="${view eq 'place'}"><c:set var="headerBackPath" value="/explore"/></c:when>
<c:when test="${(view eq 'history' or view eq 'feedback') and user.role eq 'VISITOR'}"><c:set var="headerBackPath" value="/passes"/></c:when>
<c:when test="${view eq 'pass'}"><c:set var="headerBackPath" value="${user.role eq 'ADMIN' ? '/admin/passes' : '/passes'}"/></c:when>
</c:choose>
<header class="site-header"><div class="header-inner"><div class="header-leading"><c:if test="${not empty headerBackPath}"><a class="header-back" href="${root}${fn:escapeXml(headerBackPath)}" aria-label="목록으로 돌아가기"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6"/></svg></a></c:if></div><a class="brand" href="${root}/"><img class="brand-mark" src="${root}/assets/images/siheung_bi.png" width="44" height="44" alt="시흥시">시흥 투어패스</a><div aria-hidden="true"></div></div>
</header>
<main id="main" tabindex="-1">
<c:choose>
<c:when test="${view eq 'landing'}"><%@ include file="/WEB-INF/views/site/landing.jspf" %></c:when>
<c:when test="${view eq 'home' and user.role eq 'ADMIN'}"><%@ include file="/WEB-INF/views/admin/admin-home.jspf" %></c:when>
<c:when test="${view eq 'home'}">
<div class="home-layout home-dashboard">
<header class="home-heading home-welcome"><img class="welcome-character" src="${root}/assets/images/siheung_character.png" width="640" height="640" alt=""><div><h1>나만의 시흥 여행을 담다</h1><p>새로운 여행을 찾고, 준비하던 여행을 이어가세요.</p></div></header>
<%@ include file="/WEB-INF/views/site/home-background.jspf" %>
<div class="home-card-grid">
<a class="home-card home-start" href="${root}${user.role eq 'VISITOR' ? '/merchants' : '/explore'}"><div><span class="home-card-label">장소 · 행사 · 음식점</span><h2>새 여행 만들기</h2><p>투어패스 상품을 고르거나<br>직접 카드를 담아 시작하세요.</p></div><span class="home-card-action">${user.role eq 'VISITOR' ? '전체 상품 보기' : '카드 둘러보기'} <span aria-hidden="true">↗</span></span></a>
<c:if test="${user.role eq 'VISITOR'}">
<a class="home-card home-drafts" href="${root}/journeys"><div><h2>만들던 여행</h2><strong class="home-card-count">${homeDraftCount}<span>개 제작 중</span></strong><p class="home-draft-name"><c:out value="${empty homeLatestDraft ? '아직 제작 중인 여행이 없어요.' : homeLatestDraft.name}"/></p></div><span class="home-card-action">${empty homeLatestDraft ? '제작 중 목록' : '이어 만들기'} <span aria-hidden="true">→</span></span></a>
<a class="home-card home-passes" href="${root}/passes"><div><h2>진행 중인 여행</h2><strong class="home-card-count">${homePassCount}<span>개 제작 확정</span></strong><p>방문 날짜와 메모를 남겨 보세요.</p></div><span class="home-card-action">패스 보기 <span aria-hidden="true">→</span></span></a>
</c:if>
</div>
<c:if test="${user.role eq 'VISITOR'}"><details class="guide-section home-how-to"><summary>이용 방법</summary><%@ include file="/WEB-INF/views/site/how-to.jspf" %></details></c:if>
</div>
</c:when>
<c:when test="${view eq 'place-feedback-statistics'}"><%@ include file="/WEB-INF/views/admin/place-feedback-statistics.jspf" %></c:when>
<c:when test="${view eq 'public-card'}"><%@ include file="/WEB-INF/views/catalog/public-card.jspf" %></c:when>
<c:when test="${view eq 'profile'}"><%@ include file="/WEB-INF/views/site/profile.jspf" %></c:when>
<c:when test="${view eq 'journey-builder'}"><%@ include file="/WEB-INF/views/journey/journey-builder.jspf" %></c:when>
<c:when test="${view eq 'journey-photocards'}"><%@ include file="/WEB-INF/views/journey/journey-photocards.jspf" %></c:when>
<c:when test="${view eq 'journeys' or view eq 'journey'}"><%@ include file="/WEB-INF/views/journey/journeys.jspf" %></c:when>
<c:when test="${view eq 'error'}"><h1>요청 확인</h1><p class="error" role="alert"><c:out value="${message}"/></p><p>오류 코드: <c:out value="${errorCode}"/></p><a href="${root}/">처음으로</a></c:when>
<c:when test="${view eq 'login'}"><%@ include file="/WEB-INF/views/site/login.jspf" %></c:when>
<c:when test="${view eq 'catalog' or view eq 'product' or view eq 'merchant' or view eq 'place'}"><%@ include file="/WEB-INF/views/catalog/catalog.jspf" %></c:when>
<c:when test="${view eq 'recommendations'}"><%@ include file="/WEB-INF/views/catalog/recommendations.jspf" %></c:when>
<c:when test="${view eq 'passes' or view eq 'pass'}"><%@ include file="/WEB-INF/views/pass/passes.jspf" %></c:when>
<c:when test="${view eq 'checkout'}"><%@ include file="/WEB-INF/views/pass/checkout.jspf" %></c:when>
<c:when test="${view eq 'redeem' or view eq 'confirm' or view eq 'history' or view eq 'feedback'}"><%@ include file="/WEB-INF/views/pass/usage.jspf" %></c:when>
<c:when test="${view eq 'admin' or view eq 'editor' or view eq 'relations' or view eq 'goods'}"><%@ include file="/WEB-INF/views/admin/admin.jspf" %></c:when>
</c:choose></main><c:if test="${view ne 'landing' and view ne 'login'}"><nav class="bottom-nav" aria-label="주 메뉴"><div class="bottom-nav-inner">
<a href="${root}/" ${view eq 'home' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7v11h-6v-7H9v7H3V10Z"/></svg><span>홈</span></a>
<c:if test="${empty user or user.role eq 'VISITOR'}"><a href="${root}/journeys" ${journeyStatus eq 'PLANNING' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 6V4h8v2M5 6h14v15H5V6Zm4 4v7m6-7v7"/></svg><span>여행 만들기</span></a>
<a href="${root}/passes" ${view eq 'passes' or view eq 'pass' or view eq 'checkout' or view eq 'history' or view eq 'feedback' or journeyStatus eq 'CONFIRMED' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4V6Zm12 2v2m0 2v2m0 2v1"/></svg><span>내 투어패스</span></a>
<a href="${root}/journeys?status=ARCHIVED" ${journeyStatus eq 'ARCHIVED' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 5h16v16H4V5Zm4-2v4m8-4v4M8 11h8m-8 4h5"/></svg><span>내 여행</span></a>
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
