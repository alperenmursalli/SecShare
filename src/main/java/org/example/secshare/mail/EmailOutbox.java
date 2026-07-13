package org.example.secshare.mail;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A durable outbound-email record. Sending a large audience's links means enqueuing one row
 * per recipient here; a scheduled worker drains PENDING rows in rate-limited batches and
 * retries failures, so a restart never loses queued mail. Column lengths are explicit so
 * {@code ddl-auto=update} never tries to re-alter the types on later starts.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    @Id
    private UUID id;

    @Column(name = "recipient", nullable = false, length = 254)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EmailStatus status = EmailStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public EmailStatus getStatus() {
        return status;
    }

    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
