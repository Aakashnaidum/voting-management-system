package io.github.aakashnaidum.voting;

import static org.junit.jupiter.api.Assertions.*;

import io.github.aakashnaidum.voting.model.Role;
import io.github.aakashnaidum.voting.model.Row;
import io.github.aakashnaidum.voting.model.User;
import io.github.aakashnaidum.voting.service.VotingException;
import io.github.aakashnaidum.voting.web.AppContext;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VotingServiceTest {
    private static final char[] PW = "long-enough-password".toCharArray();
    private static final int ELECTION = 1;  // OPEN, from demo-data.sql
    private AppContext app;
    private int nomA, nomB, nomOther;

    private User approved(String email, Role role, int constituency) {
        int id = app.users.register(email, PW, email, null, role, constituency);
        app.users.setStatus(id, "APPROVED");
        return app.users.find(id).orElseThrow();
    }

    private User voter(String email, int constituency) {
        return approved(email, Role.VOTER, constituency);
    }

    @BeforeEach
    void setUp() {
        app = TestDb.freshApp();
        nomA = app.elections.nominate(approved("ca@example.org", Role.CANDIDATE, 1), ELECTION, "Party A");
        nomB = app.elections.nominate(approved("cb@example.org", Role.CANDIDATE, 1), ELECTION, "Party B");
        nomOther = app.elections.nominate(approved("cc@example.org", Role.CANDIDATE, 2), ELECTION, "Party C");
        for (int n : new int[] {nomA, nomB, nomOther}) app.elections.setNominationStatus(n, "APPROVED");
    }

    @Test
    void approvedVoterCanVoteExactlyOnce() {
        User v = voter("v1@example.org", 1);
        app.voting.castVote(v, ELECTION, nomA);
        assertTrue(app.voting.hasVoted(v.id, ELECTION));
        VotingException e = assertThrows(VotingException.class, () -> app.voting.castVote(v, ELECTION, nomB));
        assertTrue(e.getMessage().contains("already voted"));
        assertEquals(1, app.voting.turnout(ELECTION));
    }

    @Test
    void failedSecondVoteLeavesNoPartialBallot() {
        User v = voter("v1@example.org", 1);
        app.voting.castVote(v, ELECTION, nomA);
        assertThrows(VotingException.class, () -> app.voting.castVote(v, ELECTION, nomA));
        assertTrue(app.voting.verifyChain(ELECTION).isEmpty(), "rollback must keep ballots == participation");
    }

    @Test
    void pendingVotersAndNonVotersCannotVote() {
        int pendingId = app.users.register("p@example.org", PW, "Pending", null, Role.VOTER, 1);
        User pending = app.users.find(pendingId).orElseThrow();
        assertThrows(VotingException.class, () -> app.voting.castVote(pending, ELECTION, nomA));
        User cand = app.users.find(approved("x@example.org", Role.CANDIDATE, 1).id).orElseThrow();
        assertThrows(VotingException.class, () -> app.voting.castVote(cand, ELECTION, nomA));
    }

    @Test
    void cannotVoteOutsideOwnConstituencyOrForUnapprovedNomination() {
        User v = voter("v1@example.org", 1);
        assertThrows(VotingException.class, () -> app.voting.castVote(v, ELECTION, nomOther));
        int pendingNom = app.elections.nominate(approved("cd@example.org", Role.CANDIDATE, 1), ELECTION, "Party D");
        assertThrows(VotingException.class, () -> app.voting.castVote(v, ELECTION, pendingNom));
        assertFalse(app.voting.hasVoted(v.id, ELECTION));
    }

    @Test
    void votingOnlyWhileElectionIsOpen() {
        User v = voter("v1@example.org", 1);
        app.elections.setElectionStatus(ELECTION, "CLOSED");
        assertThrows(VotingException.class, () -> app.voting.castVote(v, ELECTION, nomA));
        assertThrows(VotingException.class, () -> app.elections.setElectionStatus(ELECTION, "OPEN"), "no reopening");
        int draft = app.elections.createElection("Draft election");
        assertThrows(VotingException.class, () -> app.voting.castVote(v, draft, nomA));
    }

    @Test
    void resultsCountBallots() {
        app.voting.castVote(voter("v1@example.org", 1), ELECTION, nomA);
        app.voting.castVote(voter("v2@example.org", 1), ELECTION, nomA);
        app.voting.castVote(voter("v3@example.org", 1), ELECTION, nomB);
        app.voting.castVote(voter("v4@example.org", 2), ELECTION, nomOther);
        List<Row> rows = app.voting.results(ELECTION);
        assertEquals(3, rows.size());
        assertEquals("Party A", rows.get(0).get("party"));
        assertEquals(2L, ((Number) rows.get(0).get("votes")).longValue());
        assertEquals(4, app.voting.turnout(ELECTION));
    }

    @Test
    void ballotsDoNotReferenceVoters() throws Exception {
        try (Connection c = app.db.connect(); var rs = c.getMetaData().getColumns(null, null, "ballots", null)) {
            while (rs.next()) assertFalse(rs.getString("COLUMN_NAME").toLowerCase().contains("voter"));
        }
    }

    @Test
    void hashChainDetectsTampering() throws Exception {
        for (int i = 0; i < 3; i++) app.voting.castVote(voter("v" + i + "@example.org", 1), ELECTION, nomA);
        assertTrue(app.voting.verifyChain(ELECTION).isEmpty());
        try (Connection c = app.db.connect()) {
            c.createStatement().executeUpdate("UPDATE ballots SET nomination_id = " + nomB + " WHERE seq = 2");
        }
        List<String> problems = app.voting.verifyChain(ELECTION);
        assertTrue(problems.stream().anyMatch(p -> p.contains("Ballot 2")), problems.toString());
        try (Connection c = app.db.connect()) {
            c.createStatement().executeUpdate("DELETE FROM ballots WHERE seq = 3");
        }
        assertTrue(app.voting.verifyChain(ELECTION).stream().anyMatch(p -> p.contains("differs")));
    }

    @Test
    void concurrentDoubleVoteAttemptsProduceOneBallot() throws Exception {
        User v = voter("racer@example.org", 1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger ok = new AtomicInteger();
        Future<?>[] fs = new Future<?>[16];
        for (int i = 0; i < fs.length; i++) {
            fs[i] = pool.submit(() -> {
                try {
                    app.voting.castVote(v, ELECTION, nomA);
                    ok.incrementAndGet();
                } catch (VotingException | IllegalStateException ignored) {
                    // expected for all but one attempt
                }
            });
        }
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(1, ok.get());
        assertEquals(1, app.voting.turnout(ELECTION));
        assertTrue(app.voting.verifyChain(ELECTION).isEmpty());
    }

    @Test
    void duplicateNominationRejected() {
        User c = app.users.find(approved("dup@example.org", Role.CANDIDATE, 1).id).orElseThrow();
        app.elections.nominate(c, ELECTION, "P");
        assertThrows(VotingException.class, () -> app.elections.nominate(c, ELECTION, "P"));
    }
}
