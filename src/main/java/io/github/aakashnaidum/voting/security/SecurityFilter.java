package io.github.aakashnaidum.voting.security;

import io.github.aakashnaidum.voting.model.Role;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Applied to every request:
 *  - role-based access: /admin/* ADMIN, /voter/* VOTER, /candidate/* CANDIDATE
 *  - CSRF: every POST must carry the per-session token
 *  - security headers
 */
@WebFilter("/*")
public class SecurityFilter implements Filter {
    public static final String CSRF_ATTR = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'");

        HttpSession session = request.getSession(true);
        if (session.getAttribute(CSRF_ATTR) == null) session.setAttribute(CSRF_ATTR, newToken());

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String expected = (String) session.getAttribute(CSRF_ATTR);
            String given = request.getParameter("csrf");
            if (given == null || !MessageDigest.isEqual(expected.getBytes(), given.getBytes())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
                return;
            }
        }

        Role required = requiredRole(request.getServletPath());
        if (required != null) {
            SessionUser user = (SessionUser) session.getAttribute(SessionUser.ATTR);
            if (user == null) {
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
            if (user.role != required) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    static Role requiredRole(String path) {
        if (path == null) return null;
        if (path.startsWith("/admin")) return Role.ADMIN;
        if (path.startsWith("/voter")) return Role.VOTER;
        if (path.startsWith("/candidate")) return Role.CANDIDATE;
        return null;
    }

    public static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
