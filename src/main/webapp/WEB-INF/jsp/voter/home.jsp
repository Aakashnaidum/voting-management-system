<%@ include file="../_header.jspf" %>
<h1>Voter</h1>
<div class="card">
  <p><c:out value="${me.fullName}"/> · <c:out value="${me.constituencyLabel}"/> · status <strong><c:out value="${me.status}"/></strong></p>
  <c:if test="${not me.approved}"><p class="muted">You can vote once an administrator approves your registration.</p></c:if>
</div>
<c:forEach var="e" items="${openElections}">
  <div class="card">
    <h2><c:out value="${e.name}"/></h2>
    <c:choose>
      <c:when test="${e.voted}"><p class="ok">You have voted in this election.</p></c:when>
      <c:when test="${empty e.ballot}"><p class="muted">No approved candidates in your constituency yet.</p></c:when>
      <c:otherwise>
        <form method="post" action="${pageContext.request.contextPath}/voter">
          <input type="hidden" name="csrf" value="${sessionScope.csrfToken}">
          <input type="hidden" name="electionId" value="${e.id}">
          <c:forEach var="b" items="${e.ballot}">
            <label class="choice"><input type="radio" name="nominationId" value="${b.id}" required>
              <c:out value="${b.candidate}"/> <span class="muted">(<c:out value="${b.party}"/>)</span></label>
          </c:forEach>
          <button type="submit">Cast vote</button>
          <p class="muted small">A vote cannot be changed once cast.</p>
        </form>
      </c:otherwise>
    </c:choose>
  </div>
</c:forEach>
<c:if test="${me.approved and empty openElections}"><p class="muted">There is no open election right now.</p></c:if>
<%@ include file="../_footer.jspf" %>
