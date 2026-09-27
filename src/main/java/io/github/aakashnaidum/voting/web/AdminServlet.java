package io.github.aakashnaidum.voting.web;

import io.github.aakashnaidum.voting.model.Role;
import io.github.aakashnaidum.voting.model.Row;
import io.github.aakashnaidum.voting.service.VotingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Election-commission functions. Access restricted to ADMIN by SecurityFilter. */
@WebServlet("/admin")
public class AdminServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.setAttribute("voters", app().users.listByRole(Role.VOTER));
        req.setAttribute("candidates", app().users.listByRole(Role.CANDIDATE));
        req.setAttribute("nominations", app().elections.nominations());
        List<Row> elections = app().elections.elections();
        List<Row> audited = new ArrayList<>();
        for (Row e : elections) {
            int id = ((Number) e.get("id")).intValue();
            audited.add(new Row().with("id", id).with("name", e.get("name")).with("status", e.get("status"))
                    .with("turnout", app().voting.turnout(id)).with("problems", app().voting.verifyChain(id)));
        }
        req.setAttribute("elections", audited);
        render(req, res, "admin/dashboard");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String action = req.getParameter("action");
        try {
            if ("userStatus".equals(action)) {
                app().users.setStatus(intParam(req, "userId"), req.getParameter("status"));
            } else if ("nominationStatus".equals(action)) {
                app().elections.setNominationStatus(intParam(req, "nominationId"), req.getParameter("status"));
            } else if ("createElection".equals(action)) {
                app().elections.createElection(req.getParameter("name"));
            } else if ("electionStatus".equals(action)) {
                app().elections.setElectionStatus(intParam(req, "electionId"), req.getParameter("status"));
            } else {
                throw new VotingException("Unknown action.");
            }
            flash(req, "Saved.");
        } catch (VotingException e) {
            flash(req, e.getMessage());
        }
        res.sendRedirect(req.getContextPath() + "/admin");
    }
}
