package io.github.aakashnaidum.voting.service;

import io.github.aakashnaidum.voting.db.Database;
import io.github.aakashnaidum.voting.model.Role;
import io.github.aakashnaidum.voting.model.Row;
import io.github.aakashnaidum.voting.model.User;
import io.github.aakashnaidum.voting.security.PasswordHasher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class UserService {
    public static final int MIN_PASSWORD_LENGTH = 10;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final Pattern MOBILE = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final String SELECT_USER =
            "SELECT u.id, u.email, u.full_name, u.mobile, u.role, u.constituency_id, u.status, u.password_hash, "
            + "c.district, c.name AS cname FROM users u LEFT JOIN constituencies c ON c.id = u.constituency_id ";

    private final Database db;

    public UserService(Database db) {
        this.db = db;
    }

    /** Self-registration for voters and candidates. New accounts start as PENDING. */
    public int register(String email, char[] password, String fullName, String mobile, Role role, int constituencyId) {
        if (role == Role.ADMIN) throw new VotingException("Administrators cannot self-register.");
        return create(email, password, fullName, mobile, role, constituencyId, "PENDING");
    }

    public int createAdmin(String email, char[] password, String fullName) {
        return create(email, password, fullName, null, Role.ADMIN, null, "APPROVED");
    }

    private int create(String email, char[] password, String fullName, String mobile, Role role,
                       Integer constituencyId, String status) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) throw new VotingException("Enter a valid e-mail address.");
        if (password == null || password.length < MIN_PASSWORD_LENGTH)
            throw new VotingException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        if (fullName == null || fullName.trim().isEmpty() || fullName.length() > 120)
            throw new VotingException("Enter your full name (max 120 characters).");
        if (mobile != null && !mobile.isEmpty() && !MOBILE.matcher(mobile).matches())
            throw new VotingException("Enter a valid mobile number (10-15 digits).");
        String sql = "INSERT INTO users (email, password_hash, full_name, mobile, role, constituency_id, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, normalized);
            ps.setString(2, PasswordHasher.hash(password));
            ps.setString(3, fullName.trim());
            ps.setString(4, mobile == null || mobile.isEmpty() ? null : mobile);
            ps.setString(5, role.name());
            if (constituencyId == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setInt(6, constituencyId);
            ps.setString(7, status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new VotingException("That e-mail is already registered, or the constituency is invalid.");
        } catch (SQLException e) {
            if (isConstraintViolation(e))
                throw new VotingException("That e-mail is already registered, or the constituency is invalid.");
            throw new IllegalStateException(e);
        }
    }

    static boolean isConstraintViolation(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23");
    }

    /** Returns the user only if the password matches. Pending/rejected users may log in to see their status. */
    public Optional<User> authenticate(String email, char[] password) {
        if (email == null || password == null) return Optional.empty();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(SELECT_USER + "WHERE u.email = ?")) {
            ps.setString(1, email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    PasswordHasher.verify(password, PasswordHasher.hash("timing-equaliser".toCharArray()));
                    return Optional.empty();
                }
                return PasswordHasher.verify(password, rs.getString("password_hash"))
                        ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<User> find(int id) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(SELECT_USER + "WHERE u.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<User> listByRole(Role role) {
        List<User> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(SELECT_USER + "WHERE u.role = ? ORDER BY u.status DESC, u.id")) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void setStatus(int userId, String status) {
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) throw new VotingException("Invalid status.");
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET status = ? WHERE id = ? AND role <> 'ADMIN'")) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            if (ps.executeUpdate() != 1) throw new VotingException("User not found.");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean adminExists() {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE role = 'ADMIN'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Row> constituencies() {
        List<Row> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT id, district, name FROM constituencies ORDER BY district, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Row().with("id", rs.getInt("id"))
                        .with("label", rs.getString("district") + " - " + rs.getString("name")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static User map(ResultSet rs) throws SQLException {
        int cid = rs.getInt("constituency_id");
        Integer constituency = rs.wasNull() ? null : cid;
        String label = constituency == null ? null : rs.getString("district") + " - " + rs.getString("cname");
        return new User(rs.getInt("id"), rs.getString("email"), rs.getString("full_name"), rs.getString("mobile"),
                Role.valueOf(rs.getString("role")), constituency, label, rs.getString("status"));
    }
}
