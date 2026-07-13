package org.example.secshare.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Enqueues outbound email. Always present so callers can enqueue unconditionally; whether
 * anything is actually delivered depends on {@link #isConfigured()} (mail enabled + a public
 * base URL for building absolute links). Delivery itself is handled by {@code OutboxMailSender}.
 */
@Service
public class EmailOutboxService {

    private final EmailOutboxRepository repository;
    private final boolean enabled;
    private final String publicBaseUrl;

    public EmailOutboxService(
            EmailOutboxRepository repository,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.repository = repository;
        this.enabled = enabled;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
    }

    /** True when mail is enabled and a base URL is set so absolute links can be built. */
    public boolean isConfigured() {
        return enabled && !publicBaseUrl.isBlank();
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public void enqueue(String recipient, String subject, String body) {
        EmailOutbox mail = new EmailOutbox();
        mail.setId(UUID.randomUUID());
        mail.setRecipient(recipient);
        mail.setSubject(truncate(subject, 300));
        mail.setBody(truncate(body, 4000));
        mail.setStatus(EmailStatus.PENDING);
        mail.setCreatedAt(Instant.now());
        repository.save(mail);
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
