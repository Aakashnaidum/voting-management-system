<%@ include file="../_header.jspf" %>
<h1>Administrator</h1>

<div class="card"><h2>Elections</h2><table>
  <tr><th>Election</th><th>Status</th><th>Turnout</th><th>Integrity</th><th></th></tr>
  <c:forEach var="e" items="${elections}">
    <tr><td><c:out value="${e.name}"/></td><td><c:out value="${e.status}"/></td><td><c:out value="${e.turnout}"/></td>
      <td><c:choose><c:when test="${empty e.problems}"><span class="ok">chain OK</span></c:when>
          <c:otherwise><span class="bad"><c:out value="${e.problems}"/></span></c:otherwise></c:choose></td>
      <td>
        <c:if test="${e.status == 'DRAFT' or e.status == 'OPEN'}">
        <form method="post" action="${pageContext.request.contextPath}/admin" class="inline">
          <input type="hidden" name="csrf" value="${sessionScope.csrfToken}">
          <input type="hidden" name="action" value="electionStatus"><input type="hidden" name="electionId" value="${e.id}">
          <input type="hidden" name="status" value="${e.status == 'DRAFT' ? 'OPEN' : 'CLOSED'}">
          <button type="submit">${e.status == 'DRAFT' ? 'Open voting' : 'Close &amp; publish'}</button>
        </form></c:if>
        <c:if test="${e.status == 'CLOSED'}"><a href="${pageContext.request.contextPath}/results?election=${e.id}">Results</a></c:if>
      </td></tr>
  </c:forEach>
</table>
<form method="post" action="${pageContext.request.contextPath}/admin" class="row">
  <input type="hidden" name="csrf" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="createElection">
  <input name="name" required maxlength="160" placeholder="New election name"><button type="submit">Create election</button>
</form></div>

<div class="card"><h2>Nominations</h2><table>
  <tr><th>Election</th><th>Candidate</th><th>Party</th><th>Constituency</th><th>Status</th><th></th></tr>
  <c:forEach var="n" items="${nominations}">
    <tr><td><c:out value="${n.election}"/></td><td><c:out value="${n.candidate}"/></td><td><c:out value="${n.party}"/></td>
      <td><c:out value="${n.constituency}"/></td><td><c:out value="${n.status}"/></td>
      <td><c:if test="${n.status == 'PENDING'}">
        <c:forEach var="s" items="APPROVED,REJECTED">
          <form method="post" action="${pageContext.request.contextPath}/admin" class="inline">
            <input type="hidden" name="csrf" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="nominationStatus">
            <input type="hidden" name="nominationId" value="${n.id}"><input type="hidden" name="status" value="${s}">
            <button type="submit">${s == 'APPROVED' ? 'Approve' : 'Reject'}</button></form>
        </c:forEach></c:if></td></tr>
  </c:forEach>
</table></div>

<c:forEach var="group" items="${['voters','candidates']}">
<div class="card"><h2>${group == 'voters' ? 'Voters' : 'Candidate accounts'}</h2><table>
  <tr><th>Name</th><th>E-mail</th><th>Constituency</th><th>Status</th><th></th></tr>
  <c:forEach var="u" items="${requestScope[group]}">
    <tr><td><c:out value="${u.fullName}"/></td><td><c:out value="${u.email}"/></td><td><c:out value="${u.constituencyLabel}"/></td>
      <td><c:out value="${u.status}"/></td>
      <td><c:if test="${u.status == 'PENDING'}">
        <c:forEach var="s" items="APPROVED,REJECTED">
          <form method="post" action="${pageContext.request.contextPath}/admin" class="inline">
            <input type="hidden" name="csrf" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="userStatus">
            <input type="hidden" name="userId" value="${u.id}"><input type="hidden" name="status" value="${s}">
            <button type="submit">${s == 'APPROVED' ? 'Approve' : 'Reject'}</button></form>
        </c:forEach></c:if></td></tr>
  </c:forEach>
</table></div>
</c:forEach>
<%@ include file="../_footer.jspf" %>
