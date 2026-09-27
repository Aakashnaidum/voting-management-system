<%@ include file="_header.jspf" %>
<h1>Voting Management System</h1>
<p>An educational web application for running a small constituency-based election:
voters and candidates register, an administrator approves them and the candidates' nominations,
approved voters cast one ballot each, and results are published when the election closes.</p>
<div class="card">
  <h2>Published results</h2>
  <c:forEach var="e" items="${closedElections}">
    <p><a href="${pageContext.request.contextPath}/results?election=${e.id}"><c:out value="${e.name}"/></a></p>
  </c:forEach>
  <c:if test="${empty closedElections}"><p class="muted">No closed elections yet.</p></c:if>
</div>
<%@ include file="_footer.jspf" %>
