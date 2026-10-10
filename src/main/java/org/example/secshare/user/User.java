package org.example.secshare.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="users")
public class User{
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash",nullable=false)
    private String passwordHash;

    @Column(nullable = false)
    private String roles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Email ownership proof. Until this is true the account may only touch its own uploads;
    // it must never receive files shared to its address (a USER grant or audience membership),
    // otherwise registering an unclaimed address would hand over someone else's files.
    @Column(name = "email_verified", nullable = false, columnDefinition = "boolean not null default false")
    private boolean emailVerified;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public void setVerificationExpiresAt(Instant verificationExpiresAt) { this.verificationExpiresAt = verificationExpiresAt; }
}



