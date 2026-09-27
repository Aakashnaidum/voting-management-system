<%@ include file="../_header.jspf" %>
<h1>Candidate</h1>
<div class="card">
  <p><c:out value="${me.fullName}"/> · <c:out value="${me.constituencyLabel}"/> · status <strong><c:out value="${me.status}"/></strong></p>
</div>
<c:if test="${me.approved}">
<div class="card narrow">
  <h2>File a nomination</h2>
  <form method="post" action="${pageContext.request.contextPath}/candidate">
    <input type="hidden" name="csrf" value="${sessionScope.csrfToken}">
    <label>Election <select name="electionId">
      <c:forEach var="e" items="${elections}"><option value="${e.id}"><c:out value="${e.name}"/> (<c:out value="${e.status}"/>)</option></c:forEach>
    </select></label>
    <label>Party <input name="party" required maxlength="120" placeholder="Independent"></label>
    <button type="submit">Submit nomination</button>
  </form>
</div>
</c:if>
<div class="card"><h2>My nominations</h2><table>
  <tr><th>Election</th><th>Party</th><th>Status</th></tr>
  <c:forEach var="n" items="${nominations}">
    <tr><td><c:out value="${n.election}"/></td><td><c:out value="${n.party}"/></td><td><c:out value="${n.status}"/></td></tr>
  </c:forEach>
</table></div>
<%@ include file="../_footer.jspf" %>
