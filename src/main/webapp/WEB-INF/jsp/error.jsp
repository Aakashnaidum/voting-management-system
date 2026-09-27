<%@ page isErrorPage="true" %>
<%@ include file="_header.jspf" %>
<h1>Something went wrong</h1>
<p>Status <c:out value="${requestScope['javax.servlet.error.status_code']}"/>: <c:out value="${requestScope['javax.servlet.error.message']}"/></p>
<p><a href="${pageContext.request.contextPath}/">Home</a></p>
<%@ include file="_footer.jspf" %>
