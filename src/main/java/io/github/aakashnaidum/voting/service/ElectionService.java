package io.github.aakashnaidum.voting.service;

import io.github.aakashnaidum.voting.db.Database;
import io.github.aakashnaidum.voting.model.Row;
import io.github.aakashnaidum.voting.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Elections and candidate nominations. */
public class ElectionService {
    private final Database db;

    public ElectionService(Database db) {
        this.db = db;
    }

    public int createElection(String name) {
        if (name == null || name.trim().isEmpty() || name.length() > 160) throw new VotingException("Enter an election name.");
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO elections (name, status) VALUES (?, 'DRAFT')",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return k.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Allowed transitions: DRAFT -> OPEN -> CLOSED. Closed elections cannot be reopened. */
    public void setElectionStatus(int electionId, String target) {
        String from;
        if ("OPEN".equals(target)) from = "DRAFT";
        else if ("CLOSED".equals(target)) from = "OPEN";
        else throw new VotingException("Invalid election status.");
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE elections SET status = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, target);
            ps.setInt(2, electionId);
            ps.setString(3, from);
            if (ps.executeUpdate() != 1) throw new VotingException("Election must be " + from + " to become " + target + ".");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Row> elections() {
        return query("SELECT id, name, status FROM elections ORDER BY id DESC");
    }

    public List<Row> electionsWithStatus(String status) {
        List<Row> out = new ArrayList<>();
        for (Row r : elections()) if (status.equals(r.get("status"))) out.add(r);
        return out;
    }

    public String electionStatus(Connection c, int electionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status FROM elections WHERE id = ?")) {
            ps.setInt(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new VotingException("Election not found.");
                return rs.getString(1);
            }
        }
    }

    /** A candidate nominates themself in their own constituency for an election that has not closed. */
    public int nominate(User candidate, int electionId, String party) {
        if (!candidate.isApproved()) throw new VotingException("Your candidate account has not been approved yet.");
        if (candidate.constituencyId == null) throw new VotingException("Your account has no constituency.");
        if (party == null || party.trim().isEmpty() || party.length() > 120) throw new VotingException("Enter a party name (or 'Independent').");
        try (Connection c = db.connect()) {
            if ("CLOSED".equals(electionStatus(c, electionId))) throw new VotingException("That election is closed.");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO nominations (election_id, candidate_user_id, constituency_id, party, status) VALUES (?, ?, ?, ?, 'PENDING')",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, electionId);
                ps.setInt(2, candidate.id);
                ps.setInt(3, candidate.constituencyId);
                ps.setString(4, party.trim());
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) {
                    k.next();
                    return k.getInt(1);
                }
            }
        } catch (SQLException e) {
            if (UserService.isConstraintViolation(e)) throw new VotingException("You have already filed a nomination for this election.");
            throw new IllegalStateException(e);
        }
    }

    public void setNominationStatus(int nominationId, String status) {
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) throw new VotingException("Invalid status.");
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE nominations SET status = ? WHERE id = ? AND election_id IN (SELECT id FROM elections WHERE status <> 'CLOSED')")) {
            ps.setString(1, status);
            ps.setInt(2, nominationId);
            if (ps.executeUpdate() != 1) throw new VotingException("Nomination not found or election closed.");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Row> nominations() {
        return query("SELECT n.id, e.name AS election, u.full_name AS candidate, n.party, "
                + "CONCAT(c.district, ' - ', c.name) AS constituency, n.status "
                + "FROM nominations n JOIN elections e ON e.id = n.election_id JOIN users u ON u.id = n.candidate_user_id "
                + "JOIN constituencies c ON c.id = n.constituency_id ORDER BY n.status DESC, n.id");
    }

    public List<Row> nominationsOf(int candidateId) {
        List<Row> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT n.id, e.name AS election, e.status AS election_status, n.party, n.status "
                        + "FROM nominations n JOIN elections e ON e.id = n.election_id WHERE n.candidate_user_id = ? ORDER BY n.id DESC")) {
            ps.setInt(1, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** Approved candidates on a voter's ballot. */
    public List<Row> ballot(int electionId, int constituencyId) {
        List<Row> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT n.id, u.full_name AS candidate, n.party FROM nominations n JOIN users u ON u.id = n.candidate_user_id "
                        + "WHERE n.election_id = ? AND n.constituency_id = ? AND n.status = 'APPROVED' ORDER BY u.full_name")) {
            ps.setInt(1, electionId);
            ps.setInt(2, constituencyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private List<Row> query(String sql) {
        List<Row> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(row(rs));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    static Row row(ResultSet rs) throws SQLException {
        Row r = new Row();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            r.put(rs.getMetaData().getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), rs.getObject(i));
        }
        return r;
    }
}
