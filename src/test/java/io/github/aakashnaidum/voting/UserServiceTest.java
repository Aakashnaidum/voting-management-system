package io.github.aakashnaidum.voting;

import static org.junit.jupiter.api.Assertions.*;

import io.github.aakashnaidum.voting.model.Role;
import io.github.aakashnaidum.voting.model.User;
import io.github.aakashnaidum.voting.service.VotingException;
import io.github.aakashnaidum.voting.web.AppContext;
import java.sql.Connection;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class UserServiceTest {
    private final AppContext app = TestDb.freshApp();
    private static final char[] PW = "long-enough-password".toCharArray();

    @Test
    void registrationStoresHashNotPasswordAndStartsPending() throws Exception {
        int id = app.users.register("Voter@Example.org ", PW, "A Voter", "9000000001", Role.VOTER, 1);
        User u = app.users.find(id).orElseThrow();
        assertEquals("voter@example.org", u.email);
        assertEquals("PENDING", u.status);
        try (Connection c = app.db.connect(); ResultSet rs = c.createStatement()
                .executeQuery("SELECT password_hash FROM users WHERE id = " + id)) {
            rs.next();
            assertTrue(rs.getString(1).startsWith("pbkdf2_sha256$"));
        }
    }

    @Test
    void loginRequiresCorrectPassword() {
        app.users.register("v@example.org", PW, "A Voter", null, Role.VOTER, 1);
        assertTrue(app.users.authenticate("v@example.org", PW).isPresent());
        assertTrue(app.users.authenticate("V@EXAMPLE.ORG", PW).isPresent());
        assertTrue(app.users.authenticate("v@example.org", "wrong-password!".toCharArray()).isEmpty());
        assertTrue(app.users.authenticate("nobody@example.org", PW).isEmpty());
    }

    @Test
    void sqlInjectionInLoginIsInert() {
        app.users.register("v@example.org", PW, "A Voter", null, Role.VOTER, 1);
        assertTrue(app.users.authenticate("v@example.org' OR '1'='1", "' OR '1'='1".toCharArray()).isEmpty());
        assertTrue(app.users.authenticate("' OR 1=1 --", PW).isEmpty());
    }

    @Test
    void validationAndDuplicates() {
        assertThrows(VotingException.class, () -> app.users.register("bad-email", PW, "X", null, Role.VOTER, 1));
        assertThrows(VotingException.class, () -> app.users.register("a@example.org", "short".toCharArray(), "X", null, Role.VOTER, 1));
        assertThrows(VotingException.class, () -> app.users.register("a@example.org", PW, "X", "12ab", Role.VOTER, 1));
        assertThrows(VotingException.class, () -> app.users.register("a@example.org", PW, "X", null, Role.ADMIN, 1));
        assertThrows(VotingException.class, () -> app.users.register("a@example.org", PW, "X", null, Role.VOTER, 999));
        app.users.register("a@example.org", PW, "X", null, Role.VOTER, 1);
        assertThrows(VotingException.class, () -> app.users.register("A@example.org", PW, "Y", null, Role.VOTER, 1));
    }

    @Test
    void adminAccountsCannotBeDemotedThroughStatusChanges() {
        int admin = app.users.createAdmin("root@example.org", PW, "Admin");
        assertThrows(VotingException.class, () -> app.users.setStatus(admin, "REJECTED"));
        assertThrows(VotingException.class, () -> app.users.setStatus(admin, "WHATEVER"));
    }
}
