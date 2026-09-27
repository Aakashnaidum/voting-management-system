package io.github.aakashnaidum.voting.web;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Public results, available only after an election is CLOSED. */
@WebServlet("/results")
public class ResultsServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        int id = intParam(req, "election");
        boolean closed = app().elections.electionsWithStatus("CLOSED").stream()
                .anyMatch(e -> ((Number) e.get("id")).intValue() == id);
        if (!closed) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Results are published after the election closes.");
            return;
        }
        req.setAttribute("results", app().voting.results(id));
        req.setAttribute("turnout", app().voting.turnout(id));
        req.setAttribute("problems", app().voting.verifyChain(id));
        render(req, res, "results");
    }
}
