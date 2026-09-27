<%@ include file="_header.jspf" %>
<h1>Register</h1>
<div class="card narrow">
<form method="post" action="${pageContext.request.contextPath}/register">
  <input type="hidden" name="csrf" value="${sessionScope.csrfToken}">
  <label>I am registering as
    <select name="role"><option value="VOTER">Voter</option><option value="CANDIDATE">Candidate</option></select>
  </label>
  <label>Full name <input name="fullName" required maxlength="120"></label>
  <label>E-mail <input type="email" name="email" required autocomplete="username"></label>
  <label>Mobile (optional) <input name="mobile" pattern="\+?[0-9]{10,15}"></label>
  <label>Constituency
    <select name="constituencyId" required>
      <c:forEach var="c" items="${constituencies}"><option value="${c.id}"><c:out value="${c.label}"/></option></c:forEach>
    </select>
  </label>
  <label>Password (at least 10 characters) <input type="password" name="password" required minlength="10" autocomplete="new-password"></label>
  <label>Confirm password <input type="password" name="confirm" required minlength="10" autocomplete="new-password"></label>
  <button type="submit">Register</button>
</form>
</div>
<%@ include file="_footer.jspf" %>
