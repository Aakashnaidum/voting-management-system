<%@ include file="_header.jspf" %>
<h1>Log in</h1>
<div class="card narrow">
<form method="post" action="${pageContext.request.contextPath}/login">
  <input type="hidden" name="csrf" value="${sessionScope.csrfToken}">
  <label>E-mail <input type="email" name="email" required autocomplete="username"></label>
  <label>Password <input type="password" name="password" required autocomplete="current-password"></label>
  <button type="submit">Log in</button>
</form>
</div>
<%@ include file="_footer.jspf" %>
