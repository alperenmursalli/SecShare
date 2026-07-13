package org.example.secshare.file;

import jakarta.persistence.*;
import org.example.secshare.user.User;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/**
 * A single share of a {@link SharedFile}. Unifies both sharing models via {@link #type}:
 *
 * <ul>
 *   <li>{@link ShareType#LINK}: {@link #token} holds a secret URL slug; optional
 *       {@link #passwordHash}, {@link #expiresAt}, and {@link #maxDownloads} gate access.
 *       {@link #recipient} is null.</li>
 *   <li>{@link ShareType#USER}: {@link #recipient} is the registered user the file is
 *       granted to. Link-only fields are null/unused.</li>
 * </ul>
 *
 * A share is usable only while {@link #isActive()} holds (not revoked, not expired,
 * download limit not reached).
 */
@Entity
@Table(name = "file_shares")
public class FileShare {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private SharedFile file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ShareType type;

    // --- LINK fields ---
    @Column(name = "token", unique = true)
    private String token;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    /**
     * When true, this is an ephemeral "burn" link: the underlying file is irreversibly
     * destroyed once every allowed download has been spent, i.e. when {@link #maxDownloads}
     * is reached (or after the first download when no limit is set), or when the link
     * expires unread, via the scheduled reaper.
     */
    // columnDefinition supplies a DB default so ddl-auto=update can add this NOT NULL column
    // to an already-populated table without failing on existing rows.
    @Column(name = "burn_after_access", nullable = false, columnDefinition = "boolean default false")
    private boolean burnAfterAccess = false;

    // --- USER grant fields ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    /** The audience granted access, for {@link ShareType#AUDIENCE} shares; null otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audience_id")
    private Audience audience;

    /**
     * Optional self-destruct policy for a USER or AUDIENCE grant (see {@link BurnMode}).
     * Unused by LINK shares, which burn via {@link #burnAfterAccess} instead.
     */
    // @ColumnDefault (not columnDefinition) supplies the DB default so ddl-auto=update can add
    // this NOT NULL column to an already-populated table. An explicit length keeps the mapped
    // type (varchar(16)) identical to the created column, so update mode never re-issues a
    // (malformed) "alter column ... set data type" on subsequent starts.
    @Enumerated(EnumType.STRING)
    @Column(name = "burn_mode", nullable = false, length = 16)
    @ColumnDefault("'NONE'")
    private BurnMode burnMode = BurnMode.NONE;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isDownloadLimitReached() {
        return maxDownloads != null && downloadCount >= maxDownloads;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    /** A share is usable only while not revoked, not expired, and under its download limit. */
    public boolean isActive() {
        return !revoked && !isExpired() && !isDownloadLimitReached();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SharedFile getFile() {
        return file;
    }

    public void setFile(SharedFile file) {
        this.file = file;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public ShareType getType() {
        return type;
    }

    public void setType(ShareType type) {
        this.type = type;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getMaxDownloads() {
        return maxDownloads;
    }

    public void setMaxDownloads(Integer maxDownloads) {
        this.maxDownloads = maxDownloads;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public boolean isBurnAfterAccess() {
        return burnAfterAccess;
    }

    public void setBurnAfterAccess(boolean burnAfterAccess) {
        this.burnAfterAccess = burnAfterAccess;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public Audience getAudience() {
        return audience;
    }

    public void setAudience(Audience audience) {
        this.audience = audience;
    }

    public BurnMode getBurnMode() {
        return burnMode;
    }

    public void setBurnMode(BurnMode burnMode) {
        this.burnMode = burnMode;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
