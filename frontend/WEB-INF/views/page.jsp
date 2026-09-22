<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="root" value="${pageContext.request.contextPath}"/>
<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><title>시흥 투어패스</title><link rel="stylesheet" href="${root}/assets/style.css"><script src="${root}/assets/site.js" defer></script></head><body class="page-${fn:escapeXml(view)}"><a class="skip-link" href="#main">본문으로 건너뛰기</a>
<header class="site-header"><div class="header-inner"><a class="brand" href="${root}/"><svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true"><path fill="currentColor" d="M16 2a11 11 0 0 0-11 11c0 8 11 17 11 17s11-9 11-17A11 11 0 0 0 16 2Zm0 6a5 5 0 1 1 0 10 5 5 0 0 1 0-10Z"/></svg>시흥 투어패스</a></div></header>
<main id="main" tabindex="-1"><p class="notice"><strong>시연용·실제 사용 불가</strong><span>혜택 이용권·가맹점·금액은 가상 설정입니다. 여행 패스는 개인 계획·기록입니다.</span></p>
<c:choose>
<c:when test="${view eq 'home'}">
<div class="home-layout"><section class="hero"><h1>나만의 시흥 여행을 담다</h1><p>어디로 떠날지 고민된다면, 관심 있는 것부터 골라 보세요.<br>내게 맞는 장소를 찾고 여행의 순간을 간직하세요.</p><div class="actions"><a class="button" href="${root}/explore">카드 골라 여행 만들기</a><a class="button secondary" href="${root}/journeys">나의 여행 패스</a></div></section>
<section class="home-guide"><h2>발견부터 기록까지, 한곳에서</h2><div class="guide-row"><span class="step" aria-hidden="true">1</span><div><h3>취향에 맞는 장소 찾기</h3><p>지역, 관심 테마, 함께 가는 사람을 고르면 추천 이유까지 알려드려요.</p><a href="${root}/events">시흥 행사 일정 살펴보기 →</a></div></div><div class="guide-row"><span class="step" aria-hidden="true">2</span><div><h3>나만의 여행에 담기</h3><p>가고 싶은 곳을 담고 순서를 정하세요. 여행 패스는 만료되지 않아요.</p></div></div><div class="guide-row"><span class="step" aria-hidden="true">3</span><div><h3>다녀온 순간 간직하기</h3><p>날짜와 메모, 만족도를 남기고 완료한 여행은 아카이브에 보관하세요.</p></div></div></section></div>
</c:when>
<c:when test="${view eq 'profile'}"><%@ include file="profile.jspf" %></c:when>
<c:when test="${view eq 'journey-builder'}"><%@ include file="journey-builder.jspf" %></c:when>
<c:when test="${view eq 'events'}"><%@ include file="events.jspf" %></c:when>
<c:when test="${view eq 'journeys' or view eq 'journey'}"><%@ include file="journeys.jspf" %></c:when>
<c:when test="${view eq 'error'}"><h1>요청 확인</h1><p class="error" role="alert"><c:out value="${message}"/></p><p>오류 코드: <c:out value="${errorCode}"/></p><a href="${root}/">처음으로</a></c:when>
<c:when test="${view eq 'login'}"><section class="panel login-panel"><h1>반가워요</h1><p>로그인하고 나만의 시흥 여행을 이어가세요.</p><form method="post" action="${root}/login"><input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}"><label>아이디<input name="loginId" maxlength="100" autocomplete="username" required></label><label>비밀번호<input type="password" name="password" maxlength="500" autocomplete="current-password" required></label><button>로그인</button></form><p class="login-alternative">먼저 둘러보고 싶다면 <a href="${root}/explore">여행 카드 둘러보기 →</a></p></section></c:when>
<c:when test="${view eq 'catalog' or view eq 'product' or view eq 'merchant' or view eq 'place'}"><%@ include file="catalog.jspf" %></c:when>
<c:when test="${view eq 'recommendations'}"><%@ include file="recommendations.jspf" %></c:when>
<c:when test="${view eq 'passes' or view eq 'pass'}"><%@ include file="passes.jspf" %></c:when>
<c:when test="${view eq 'redeem' or view eq 'confirm' or view eq 'history' or view eq 'feedback'}"><%@ include file="usage.jspf" %></c:when>
<c:when test="${view eq 'admin' or view eq 'editor' or view eq 'relations'}"><%@ include file="admin.jspf" %></c:when>
</c:choose></main><footer class="site-footer">수업용 시연 서비스 · 화면 시각은 한국 시간 · 실제 관광 자료의 영업·예약·이동 가능성은 미확인</footer><nav class="bottom-nav" aria-label="주 메뉴"><div class="bottom-nav-inner">
<a href="${root}/" ${view eq 'home' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7v11h-6v-7H9v7H3V10Z"/></svg><span>홈</span></a>
<c:if test="${empty user or user.role eq 'VISITOR'}"><a href="${root}/journeys" ${journeyStatus eq 'PLANNING' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 6V4h8v2M5 6h14v15H5V6Zm4 4v7m6-7v7"/></svg><span>여행 만들기</span></a>
<a href="${root}/journeys?status=CONFIRMED" ${journeyStatus eq 'CONFIRMED' or view eq 'passes' or view eq 'pass' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4V6Zm12 2v2m0 2v2m0 2v1"/></svg><span>내 패스</span></a>
<a href="${root}/journeys?status=ARCHIVED" ${journeyStatus eq 'ARCHIVED' or view eq 'history' or view eq 'feedback' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 5h16v16H4V5Zm4-2v4m8-4v4M8 11h8m-8 4h5"/></svg><span>여행 기록</span></a>
</c:if>
<c:if test="${user.role eq 'STAFF'}"><a href="${root}/merchant/redeem" ${view eq 'redeem' or view eq 'confirm' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg><span>혜택 사용</span></a></c:if>
<c:if test="${user.role eq 'STAFF'}"><a href="${root}/merchant/history" ${view eq 'history' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 5h16v16H4V5Zm4-2v4m8-4v4M8 11h8m-8 4h5"/></svg><span>가맹점 이력</span></a></c:if>
<c:if test="${user.role eq 'ADMIN'}"><a href="${root}/admin" ${view eq 'admin' or view eq 'editor' or view eq 'relations' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M4 17h16M8 4v6m8 4v6"/></svg><span>관리</span></a></c:if>
<a href="${root}/profile" ${view eq 'profile' or view eq 'login' ? 'aria-current=page' : ''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a7 7 0 0 0-14 0v2m7-9a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z"/></svg><span>프로필</span></a>
</div></nav></body></html>
