package io.github.aakashnaidum.voting.model;

public class User {
    public final int id;
    public final String email;
    public final String fullName;
    public final String mobile;
    public final Role role;
    public final Integer constituencyId;
    public final String constituencyLabel;
    public final String status;

    public User(int id, String email, String fullName, String mobile, Role role,
                Integer constituencyId, String constituencyLabel, String status) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.mobile = mobile;
        this.role = role;
        this.constituencyId = constituencyId;
        this.constituencyLabel = constituencyLabel;
        this.status = status;
    }

    public int getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getMobile() { return mobile; }
    public Role getRole() { return role; }
    public Integer getConstituencyId() { return constituencyId; }
    public String getConstituencyLabel() { return constituencyLabel; }
    public String getStatus() { return status; }
    public boolean isApproved() { return "APPROVED".equals(status); }
}
