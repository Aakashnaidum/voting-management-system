package io.github.aakashnaidum.voting.web;

import io.github.aakashnaidum.voting.model.Row;
import io.github.aakashnaidum.voting.model.User;
import io.github.aakashnaidum.voting.service.VotingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/candidate")
public class CandidateServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        User me = currentUser(req);
        req.setAttribute("me", me);
        req.setAttribute("nominations", app().elections.nominationsOf(me.id));
        List<Row> selectable = new ArrayList<>(app().elections.electionsWithStatus("DRAFT"));
        selectable.addAll(app().elections.electionsWithStatus("OPEN"));
        req.setAttribute("elections", selectable);
        render(req, res, "candidate/home");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            app().elections.nominate(currentUser(req), intParam(req, "electionId"), req.getParameter("party"));
            flash(req, "Nomination submitted for approval.");
        } catch (VotingException e) {
            flash(req, e.getMessage());
        }
        res.sendRedirect(req.getContextPath() + "/candidate");
    }
}
