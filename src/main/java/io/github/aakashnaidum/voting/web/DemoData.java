package io.github.aakashnaidum.voting.web;

import io.github.aakashnaidum.voting.model.Role;

/**
 * Synthetic demo accounts (invented names; example.org addresses).
 * Loaded only when VOTING_DEMO=true. The shared demo password is printed in the README.
 */
public final class DemoData {
    public static final String PASSWORD = "demo-password-2026";

    private DemoData() {}

    public static void load(AppContext app) {
        if (app.users.adminExists()) return;  // already loaded
        app.db.runScript("/db/demo-data.sql");
        char[] pw = PASSWORD.toCharArray();
        app.users.createAdmin("admin@example.org", pw, "Demo Administrator");
        int v1 = app.users.register("voter1@example.org", pw, "Asha Raman", "9000000001", Role.VOTER, 1);
        int v2 = app.users.register("voter2@example.org", pw, "Karthik Iyer", "9000000002", Role.VOTER, 1);
        app.users.register("voter3@example.org", pw, "Meena Das", "9000000003", Role.VOTER, 2);
        int c1 = app.users.register("candidate1@example.org", pw, "Ravi Kumar", "9000000011", Role.CANDIDATE, 1);
        int c2 = app.users.register("candidate2@example.org", pw, "Lakshmi Nair", "9000000012", Role.CANDIDATE, 1);
        app.users.setStatus(v1, "APPROVED");
        app.users.setStatus(v2, "APPROVED");
        app.users.setStatus(c1, "APPROVED");
        app.users.setStatus(c2, "APPROVED");
        int n1 = app.elections.nominate(app.users.find(c1).orElseThrow(), 1, "Green Future Party");
        int n2 = app.elections.nominate(app.users.find(c2).orElseThrow(), 1, "Independent");
        app.elections.setNominationStatus(n1, "APPROVED");
        app.elections.setNominationStatus(n2, "APPROVED");
    }
}
