package org.example.secshare.file;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded download of a shared file, for the owner's audit history. {@link #fileName} is a
 * snapshot so the record stays meaningful after the file is burned/deleted. Column lengths are
 * explicit so {@code ddl-auto=update} never re-alters the types on later starts.
 */
@Entity
@Table(name = "download_audit")
public class DownloadAudit {

    @Id
    private UUID id;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "share_id")
    private UUID shareId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private DownloadSource source;

    /** Recipient's email, or null for an anonymous public-link download. */
    @Column(name = "downloader_email", length = 254)
    private String downloaderEmail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "burned", nullable = false)
    @ColumnDefault("false")
    private boolean burned = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public void setFileId(UUID fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public UUID getShareId() {
        return shareId;
    }

    public void setShareId(UUID shareId) {
        this.shareId = shareId;
    }

    public DownloadSource getSource() {
        return source;
    }

    public void setSource(DownloadSource source) {
        this.source = source;
    }

    public String getDownloaderEmail() {
        return downloaderEmail;
    }

    public void setDownloaderEmail(String downloaderEmail) {
        this.downloaderEmail = downloaderEmail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isBurned() {
        return burned;
    }

    public void setBurned(boolean burned) {
        this.burned = burned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
