package io.github.aakashnaidum.voting;

import static org.junit.jupiter.api.Assertions.*;

import io.github.aakashnaidum.voting.security.PasswordHasher;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void hashesAreSaltedAndVerify() {
        String a = PasswordHasher.hash("correct horse battery".toCharArray());
        String b = PasswordHasher.hash("correct horse battery".toCharArray());
        assertNotEquals(a, b, "same password must produce different hashes (random salt)");
        assertFalse(a.contains("correct"));
        assertTrue(PasswordHasher.verify("correct horse battery".toCharArray(), a));
        assertFalse(PasswordHasher.verify("wrong password".toCharArray(), a));
    }

    @Test
    void malformedStoredValuesAreRejected() {
        assertFalse(PasswordHasher.verify("x".toCharArray(), null));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "plaintext"));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "pbkdf2_sha256$notanumber$AA==$AA=="));
    }
}
