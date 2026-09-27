package io.github.aakashnaidum.voting.security;

import io.github.aakashnaidum.voting.model.Role;
import java.io.Serializable;

/** What is kept in the HTTP session after login: the id and role, never the password. */
public class SessionUser implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ATTR = "sessionUser";
    public final int id;
    public final Role role;
    public final String name;

    public SessionUser(int id, Role role, String name) {
        this.id = id;
        this.role = role;
        this.name = name;
    }

    public int getId() { return id; }
    public Role getRole() { return role; }
    public String getName() { return name; }
}
