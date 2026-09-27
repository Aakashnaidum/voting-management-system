package io.github.aakashnaidum.voting.web;

import io.github.aakashnaidum.voting.model.User;
import io.github.aakashnaidum.voting.security.SessionUser;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

abstract class BaseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    AppContext app() {
        return AppContext.from(getServletContext());
    }

    /** Reloads the signed-in user from the database so status changes take effect immediately. */
    User currentUser(HttpServletRequest req) {
        SessionUser su = (SessionUser) req.getSession().getAttribute(SessionUser.ATTR);
        return su == null ? null : app().users.find(su.id).orElse(null);
    }

    void render(HttpServletRequest req, HttpServletResponse res, String view) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/jsp/" + view + ".jsp").forward(req, res);
    }

    void flash(HttpServletRequest req, String message) {
        req.getSession().setAttribute("flash", message);
    }

    static int intParam(HttpServletRequest req, String name) {
        try {
            return Integer.parseInt(req.getParameter(name));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
