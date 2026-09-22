<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="root" value="${pageContext.request.contextPath}"/>
<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>시흥 투어패스</title><link rel="stylesheet" href="${root}/assets/style.css"></head><body class="page-${fn:escapeXml(view)}"><a class="skip-link" href="#main">본문으로 건너뛰기</a>
<header class="site-header"><div class="header-inner"><a class="brand" href="${root}/"><svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true"><path fill="currentColor" d="M16 2a11 11 0 0 0-11 11c0 8 11 17 11 17s11-9 11-17A11 11 0 0 0 16 2Zm0 6a5 5 0 1 1 0 10 5 5 0 0 1 0-10Z"/></svg>시흥 투어패스<small>siheung-tourpass</small></a><nav aria-label="주 메뉴"><a href="${root}/merchants" ${view eq 'catalog' or view eq 'merchant' or view eq 'product' ? 'aria-current=page' : ''}>가맹점·혜택</a><a href="${root}/recommendations" ${view eq 'recommendations' or view eq 'place' ? 'aria-current=page' : ''}>관광 추천</a>
<c:choose><c:when test="${empty user}"><a href="${root}/login" ${view eq 'login' ? 'aria-current=page' : ''}>로그인</a></c:when><c:otherwise>
<c:if test="${user.role eq 'VISITOR'}"><a href="${root}/journeys" ${(view eq 'journeys' and param.status ne 'ARCHIVED') or view eq 'journey' ? 'aria-current=page' : ''}>나의 여행 패스</a><a href="${root}/journeys?status=ARCHIVED" ${view eq 'journeys' and param.status eq 'ARCHIVED' ? 'aria-current=page' : ''}>아카이브</a><a href="${root}/passes" ${view eq 'passes' or view eq 'pass' ? 'aria-current=page' : ''}>혜택 이용권</a><a href="${root}/feedback/history" ${view eq 'history' or view eq 'feedback' ? 'aria-current=page' : ''}>사용 이력·피드백</a></c:if>
<c:if test="${user.role eq 'STAFF'}"><a href="${root}/merchant/redeem" ${view eq 'redeem' or view eq 'confirm' ? 'aria-current=page' : ''}>혜택 사용</a><a href="${root}/merchant/history" ${view eq 'history' ? 'aria-current=page' : ''}>가맹점 이력</a></c:if>
<c:if test="${user.role eq 'ADMIN'}"><a href="${root}/admin" ${view eq 'admin' or view eq 'editor' or view eq 'relations' ? 'aria-current=page' : ''}>관리</a></c:if>
<div class="account"><span><c:out value="${user.displayName}"/>님</span><form class="inline" method="post" action="${root}/logout"><input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}"><button>로그아웃</button></form></div>
</c:otherwise></c:choose></nav></div></header>
<main id="main"><p class="notice"><strong>시연용·실제 사용 불가</strong><span>혜택 이용권·가맹점·금액은 가상 설정입니다. 여행 패스는 개인 계획·기록입니다.</span></p>
<c:choose>
<c:when test="${view eq 'home'}">
<section class="hero"><span class="eyebrow">취향을 따라 만나는 시흥</span><h1>나만의 시흥 여행을 담다</h1><p>어디로 떠날지 고민된다면, 관심 있는 것부터 골라 보세요.<br>내게 맞는 장소를 찾고 여행의 순간을 간직하세요.</p><div class="actions"><a class="button" href="${root}/recommendations">내 취향으로 추천받기</a><a class="button secondary" href="${root}/journeys">나의 여행 패스</a></div></section>
<section class="home-guide"><h2>발견부터 기록까지, 한곳에서</h2><div class="guide-row"><span class="step" aria-hidden="true">1</span><div><h3>취향에 맞는 장소 찾기</h3><p>지역, 관심 테마, 함께 가는 사람을 고르면 추천 이유까지 알려드려요.</p></div></div><div class="guide-row"><span class="step" aria-hidden="true">2</span><div><h3>나만의 여행에 담기</h3><p>가고 싶은 곳을 담고 순서를 정하세요. 여행 패스는 만료되지 않아요.</p></div></div><div class="guide-row"><span class="step" aria-hidden="true">3</span><div><h3>다녀온 순간 간직하기</h3><p>날짜와 메모, 만족도를 남기고 완료한 여행은 아카이브에 보관하세요.</p></div></div></section>
<a class="support-link" href="${root}/merchants"><span>선택 기능 · 시연 가맹점과 혜택 살펴보기</span><span aria-hidden="true">→</span></a></c:when>
<c:when test="${view eq 'journeys' or view eq 'journey'}"><%@ include file="journeys.jspf" %></c:when>
<c:when test="${view eq 'error'}"><h1>요청 확인</h1><p class="error" role="alert"><c:out value="${message}"/></p><p>오류 코드: <c:out value="${errorCode}"/></p><a href="${root}/">처음으로</a></c:when>
<c:when test="${view eq 'login'}"><section class="panel login-panel"><h1>반가워요</h1><p>로그인하고 나만의 시흥 여행을 이어가세요.</p><form method="post" action="${root}/login"><input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}"><label>아이디<input name="loginId" maxlength="100" autocomplete="username" required></label><label>비밀번호<input type="password" name="password" maxlength="500" autocomplete="current-password" required></label><button>로그인</button></form></section></c:when>
<c:when test="${view eq 'catalog' or view eq 'product' or view eq 'merchant' or view eq 'place'}"><%@ include file="catalog.jspf" %></c:when>
<c:when test="${view eq 'recommendations'}"><%@ include file="recommendations.jspf" %></c:when>
<c:when test="${view eq 'passes' or view eq 'pass'}"><%@ include file="passes.jspf" %></c:when>
<c:when test="${view eq 'redeem' or view eq 'confirm' or view eq 'history' or view eq 'feedback'}"><%@ include file="usage.jspf" %></c:when>
<c:when test="${view eq 'admin' or view eq 'editor' or view eq 'relations'}"><%@ include file="admin.jspf" %></c:when>
</c:choose></main><footer class="site-footer">수업용 시연 서비스 · 화면 시각은 한국 시간 · 실제 관광 자료의 영업·예약·이동 가능성은 미확인</footer></body></html>
