package org.example.secshare.file;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * One recipient of an {@link Audience}, identified by (lower-cased) email. A member need not
 * have an account yet; access is granted when someone signs in with a matching address.
 */
@Entity
@Table(
        name = "audience_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"audience_id", "email"})
)
public class AudienceMember {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audience_id", nullable = false)
    private Audience audience;

    @Column(name = "email", nullable = false)
    private String email;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Audience getAudience() {
        return audience;
    }

    public void setAudience(Audience audience) {
        this.audience = audience;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
