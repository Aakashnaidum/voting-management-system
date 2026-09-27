package io.github.aakashnaidum.voting.web;

import io.github.aakashnaidum.voting.model.Role;
import io.github.aakashnaidum.voting.model.User;
import io.github.aakashnaidum.voting.security.SecurityFilter;
import io.github.aakashnaidum.voting.security.SessionUser;
import io.github.aakashnaidum.voting.service.VotingException;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet(urlPatterns = {"", "/login", "/logout", "/register"})
public class AuthServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        switch (req.getServletPath()) {
            case "/login":
                render(req, res, "login");
                break;
            case "/register":
                req.setAttribute("constituencies", app().users.constituencies());
                render(req, res, "register");
                break;
            case "/logout":
                res.sendRedirect(req.getContextPath() + "/");
                break;
            default:
                SessionUser su = (SessionUser) req.getSession().getAttribute(SessionUser.ATTR);
                if (su != null) {
                    res.sendRedirect(req.getContextPath() + "/" + su.role.name().toLowerCase());
                } else {
                    req.setAttribute("closedElections", app().elections.electionsWithStatus("CLOSED"));
                    render(req, res, "index");
                }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        switch (req.getServletPath()) {
            case "/login":
                login(req, res);
                break;
            case "/logout":
                req.getSession().invalidate();
                res.sendRedirect(req.getContextPath() + "/");
                break;
            case "/register":
                register(req, res);
                break;
            default:
                res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String password = req.getParameter("password");
        Optional<User> user = app().users.authenticate(req.getParameter("email"),
                password == null ? null : password.toCharArray());
        if (user.isEmpty()) {
            req.setAttribute("error", "Incorrect e-mail or password.");
            render(req, res, "login");
            return;
        }
        // Prevent session fixation: issue a fresh session (and CSRF token) on login.
        req.getSession().invalidate();
        HttpSession session = req.getSession(true);
        session.setAttribute(SecurityFilter.CSRF_ATTR, SecurityFilter.newToken());
        User u = user.get();
        session.setAttribute(SessionUser.ATTR, new SessionUser(u.id, u.role, u.fullName));
        res.sendRedirect(req.getContextPath() + "/" + u.role.name().toLowerCase());
    }

    private void register(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String roleParam = req.getParameter("role");
        Role role = "CANDIDATE".equals(roleParam) ? Role.CANDIDATE : Role.VOTER;
        String password = req.getParameter("password");
        String confirm = req.getParameter("confirm");
        try {
            if (password == null || !password.equals(confirm)) throw new VotingException("Passwords do not match.");
            app().users.register(req.getParameter("email"), password.toCharArray(), req.getParameter("fullName"),
                    req.getParameter("mobile"), role, intParam(req, "constituencyId"));
            flash(req, "Registration received. An administrator must approve your account before you can "
                    + (role == Role.VOTER ? "vote." : "file a nomination."));
            res.sendRedirect(req.getContextPath() + "/login");
        } catch (VotingException e) {
            req.setAttribute("error", e.getMessage());
            req.setAttribute("constituencies", app().users.constituencies());
            render(req, res, "register");
        }
    }
}
