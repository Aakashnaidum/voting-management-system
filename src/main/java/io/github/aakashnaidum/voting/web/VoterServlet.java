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

@WebServlet("/voter")
public class VoterServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        User voter = currentUser(req);
        req.setAttribute("me", voter);
        List<Row> open = new ArrayList<>();
        if (voter != null && voter.isApproved()) {
            for (Row e : app().elections.electionsWithStatus("OPEN")) {
                int id = ((Number) e.get("id")).intValue();
                open.add(new Row().with("id", id).with("name", e.get("name"))
                        .with("voted", app().voting.hasVoted(voter.id, id))
                        .with("ballot", app().elections.ballot(id, voter.constituencyId)));
            }
        }
        req.setAttribute("openElections", open);
        render(req, res, "voter/home");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            app().voting.castVote(currentUser(req), intParam(req, "electionId"), intParam(req, "nominationId"));
            flash(req, "Your vote has been recorded.");
        } catch (VotingException e) {
            flash(req, e.getMessage());
        }
        res.sendRedirect(req.getContextPath() + "/voter");
    }
}
