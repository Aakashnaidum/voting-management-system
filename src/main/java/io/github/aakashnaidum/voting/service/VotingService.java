package io.github.aakashnaidum.voting.service;

import io.github.aakashnaidum.voting.db.Database;
import io.github.aakashnaidum.voting.model.Role;
import io.github.aakashnaidum.voting.model.Row;
import io.github.aakashnaidum.voting.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Casting, counting and auditing votes.
 *
 * <p>Double voting is prevented by the (election_id, voter_user_id) primary key of
 * {@code participation}, written in the same transaction as the ballot. Ballots
 * carry no voter reference and are chained with SHA-256 per election.
 */
public class VotingService {
    public static final String GENESIS = "0".repeat(64);
    private final Database db;
    private final ElectionService elections;

    public VotingService(Database db, ElectionService elections) {
        this.db = db;
        this.elections = elections;
    }

    public void castVote(User voter, int electionId, int nominationId) {
        if (voter.role != Role.VOTER) throw new VotingException("Only voters can vote.");
        if (!voter.isApproved()) throw new VotingException("Your voter registration has not been approved yet.");
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                // Lock the election row: serialises ballots of one election so the hash chain stays linear.
                String status;
                try (PreparedStatement lock = c.prepareStatement("SELECT status FROM elections WHERE id = ? FOR UPDATE")) {
                    lock.setInt(1, electionId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new VotingException("Election not found.");
                        status = rs.getString(1);
                    }
                }
                if (!"OPEN".equals(status)) throw new VotingException("This election is not open for voting.");

                int constituency;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT constituency_id FROM nominations WHERE id = ? AND election_id = ? AND status = 'APPROVED'")) {
                    ps.setInt(1, nominationId);
                    ps.setInt(2, electionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new VotingException("That candidate is not on the ballot.");
                        constituency = rs.getInt(1);
                    }
                }
                if (voter.constituencyId == null || constituency != voter.constituencyId)
                    throw new VotingException("You can only vote for candidates in your own constituency.");

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO participation (election_id, voter_user_id) VALUES (?, ?)")) {
                    ps.setInt(1, electionId);
                    ps.setInt(2, voter.id);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    if (UserService.isConstraintViolation(e)) throw new VotingException("You have already voted in this election.");
                    throw e;
                }

                int seq = 1;
                String prev = GENESIS;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT seq, hash FROM ballots WHERE election_id = ? ORDER BY seq DESC LIMIT 1")) {
                    ps.setInt(1, electionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            seq = rs.getInt(1) + 1;
                            prev = rs.getString(2);
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ballots (election_id, seq, nomination_id, constituency_id, prev_hash, hash) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, electionId);
                    ps.setInt(2, seq);
                    ps.setInt(3, nominationId);
                    ps.setInt(4, constituency);
                    ps.setString(5, prev);
                    ps.setString(6, ballotHash(prev, electionId, seq, nominationId, constituency));
                    ps.executeUpdate();
                }
                c.commit();
            } catch (VotingException | SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean hasVoted(int voterId, int electionId) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM participation WHERE election_id = ? AND voter_user_id = ?")) {
            ps.setInt(1, electionId);
            ps.setInt(2, voterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Vote totals per constituency and candidate. */
    public List<Row> results(int electionId) {
        List<Row> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT CONCAT(co.district, ' - ', co.name) AS constituency, u.full_name AS candidate, n.party, "
                        + "COUNT(b.id) AS votes FROM nominations n "
                        + "JOIN users u ON u.id = n.candidate_user_id JOIN constituencies co ON co.id = n.constituency_id "
                        + "LEFT JOIN ballots b ON b.nomination_id = n.id "
                        + "WHERE n.election_id = ? AND n.status = 'APPROVED' "
                        + "GROUP BY co.district, co.name, u.full_name, n.party ORDER BY constituency, votes DESC, candidate")) {
            ps.setInt(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(ElectionService.row(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public int turnout(int electionId) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM participation WHERE election_id = ?")) {
            ps.setInt(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Recomputes the hash chain. Returns an empty list when intact, otherwise the problems found. */
    public List<String> verifyChain(int electionId) {
        List<String> problems = new ArrayList<>();
        int ballots = 0;
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT seq, nomination_id, constituency_id, prev_hash, hash FROM ballots WHERE election_id = ? ORDER BY seq")) {
            ps.setInt(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                String expectedPrev = GENESIS;
                int expectedSeq = 1;
                while (rs.next()) {
                    ballots++;
                    int seq = rs.getInt(1);
                    if (seq != expectedSeq) problems.add("Ballot sequence gap: expected " + expectedSeq + ", found " + seq);
                    String prev = rs.getString(4);
                    String hash = rs.getString(5);
                    if (!prev.equals(expectedPrev)) problems.add("Ballot " + seq + ": previous-hash link broken");
                    if (!hash.equals(ballotHash(prev, electionId, seq, rs.getInt(2), rs.getInt(3))))
                        problems.add("Ballot " + seq + ": content does not match its hash");
                    expectedPrev = hash;
                    expectedSeq = seq + 1;
                }
            }
            if (ballots != turnout(electionId))
                problems.add("Ballot count (" + ballots + ") differs from number of voters who voted (" + turnout(electionId) + ")");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return problems;
    }

    static String ballotHash(String prev, int electionId, int seq, int nominationId, int constituencyId) {
        String payload = prev + "|" + electionId + "|" + seq + "|" + nominationId + "|" + constituencyId;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
