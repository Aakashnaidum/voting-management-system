<%@ include file="_header.jspf" %>
<h1>Results</h1>
<p>Turnout: <strong><c:out value="${turnout}"/></strong> ballots.
<c:choose>
  <c:when test="${empty problems}"><span class="ok">Ballot hash chain verified.</span></c:when>
  <c:otherwise><span class="bad">Integrity problems found:</span>
    <ul><c:forEach var="p" items="${problems}"><li><c:out value="${p}"/></li></c:forEach></ul></c:otherwise>
</c:choose></p>
<div class="card"><table>
  <tr><th>Constituency</th><th>Candidate</th><th>Party</th><th>Votes</th></tr>
  <c:forEach var="r" items="${results}">
    <tr><td><c:out value="${r.constituency}"/></td><td><c:out value="${r.candidate}"/></td><td><c:out value="${r.party}"/></td><td><c:out value="${r.votes}"/></td></tr>
  </c:forEach>
</table></div>
<%@ include file="_footer.jspf" %>
