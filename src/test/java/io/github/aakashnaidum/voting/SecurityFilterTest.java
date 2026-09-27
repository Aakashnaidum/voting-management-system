package io.github.aakashnaidum.voting;

import static org.junit.jupiter.api.Assertions.*;

import io.github.aakashnaidum.voting.model.Role;
import io.github.aakashnaidum.voting.security.SecurityFilter;
import io.github.aakashnaidum.voting.security.SessionUser;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

/** Exercises the filter with minimal hand-written fakes (no mocking library needed). */
class SecurityFilterTest {
    private final SecurityFilter filter = new SecurityFilter();

    /** Records what the filter did to the response. */
    static final class Recorder {
        String redirect;
        Integer error;
        boolean chained;
        final Map<String, String> headers = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, h);
    }

    private Recorder run(String method, String path, SessionUser user, String csrfParam) throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(SecurityFilter.CSRF_ATTR, "token-123");
        if (user != null) attrs.put(SessionUser.ATTR, user);
        HttpSession session = fake(HttpSession.class, (p, m, a) -> {
            if (m.getName().equals("getAttribute")) return attrs.get(a[0]);
            if (m.getName().equals("setAttribute")) attrs.put((String) a[0], a[1]);
            return null;
        });
        HttpServletRequest req = fake(HttpServletRequest.class, (p, m, a) -> {
            switch (m.getName()) {
                case "getMethod": return method;
                case "getServletPath": return path;
                case "getContextPath": return "";
                case "getSession": return session;
                case "getParameter": return "csrf".equals(a[0]) ? csrfParam : null;
                default: return null;
            }
        });
        Recorder r = new Recorder();
        HttpServletResponse res = fake(HttpServletResponse.class, (p, m, a) -> {
            switch (m.getName()) {
                case "sendRedirect": r.redirect = (String) a[0]; break;
                case "sendError": r.error = (Integer) a[0]; break;
                case "setHeader": r.headers.put((String) a[0], (String) a[1]); break;
                default: break;
            }
            return null;
        });
        FilterChain chain = (rq, rs) -> r.chained = true;
        filter.doFilter(req, res, chain);
        return r;
    }

    @Test
    void anonymousUserIsRedirectedFromProtectedPages() throws Exception {
        for (String path : new String[] {"/admin", "/voter", "/candidate"}) {
            Recorder r = run("GET", path, null, null);
            assertEquals("/login", r.redirect, path);
            assertFalse(r.chained);
        }
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        Recorder r = run("GET", "/admin", new SessionUser(5, Role.VOTER, "v"), null);
        assertEquals(403, r.error);
        assertFalse(r.chained);
        r = run("GET", "/voter", new SessionUser(1, Role.ADMIN, "a"), null);
        assertEquals(403, r.error);
    }

    @Test
    void postWithoutValidCsrfTokenIsRejected() throws Exception {
        for (String token : new String[] {null, "", "wrong"}) {
            Recorder r = run("POST", "/voter", new SessionUser(5, Role.VOTER, "v"), token);
            assertEquals(403, r.error);
            assertFalse(r.chained);
        }
        Recorder r = run("POST", "/login", null, null);  // public POSTs need the token too
        assertEquals(403, r.error);
    }

    @Test
    void correctRoleAndTokenPassThroughWithSecurityHeaders() throws Exception {
        Recorder r = run("POST", "/voter", new SessionUser(5, Role.VOTER, "v"), "token-123");
        assertTrue(r.chained);
        assertNull(r.error);
        assertEquals("DENY", r.headers.get("X-Frame-Options"));
        assertEquals("nosniff", r.headers.get("X-Content-Type-Options"));
        assertTrue(run("GET", "/", null, null).chained);
    }
}
