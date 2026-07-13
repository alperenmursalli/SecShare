package org.example.secshare.file;

import jakarta.persistence.*;
import org.example.secshare.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * A reusable, named list of recipient emails ("group") owned by a user. Sharing a file with
 * an audience creates a single {@link FileShare} of {@link ShareType#AUDIENCE} that grants
 * every {@link AudienceMember} access, so a file can reach thousands of recipients without a
 * row per person. Membership is by email: a member gains access once they sign in with a
 * matching address (account-less token delivery is a later phase).
 */
@Entity
@Table(name = "audiences")
public class Audience {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
