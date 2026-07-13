package org.example.secshare.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drains the {@link EmailOutbox} on a schedule: each tick sends up to {@code batch-size}
 * pending mails (rate limiting), marking them SENT or, after {@code max-attempts} failures,
 * FAILED. Only active when {@code app.mail.enabled=true}, which also implies an SMTP host is
 * configured so a {@link JavaMailSender} bean exists.
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class OutboxMailSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxMailSender.class);

    private final EmailOutboxRepository repository;
    private final JavaMailSender mailSender;
    private final String from;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxMailSender(
            EmailOutboxRepository repository,
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.batch-size:50}") int batchSize,
            @Value("${app.mail.max-attempts:5}") int maxAttempts
    ) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.from = from;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.mail.send-interval-ms:30000}",
            initialDelayString = "${app.mail.send-interval-ms:30000}")
    @Transactional
    public void flush() {
        List<EmailOutbox> batch = repository.findByStatusOrderByCreatedAtAsc(
                EmailStatus.PENDING, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }

        int sent = 0;
        int failed = 0;
        for (EmailOutbox mail : batch) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(mail.getRecipient());
                msg.setSubject(mail.getSubject());
                msg.setText(mail.getBody());
                mailSender.send(msg);

                mail.setStatus(EmailStatus.SENT);
                mail.setSentAt(Instant.now());
                sent++;
            } catch (Exception e) {
                mail.setAttempts(mail.getAttempts() + 1);
                mail.setLastError(truncate(e.getMessage()));
                if (mail.getAttempts() >= maxAttempts) {
                    mail.setStatus(EmailStatus.FAILED);
                    failed++;
                }
            }
            repository.save(mail);
        }

        if (sent > 0 || failed > 0) {
            log.info("Outbox flush: {} sent, {} permanently failed", sent, failed);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "unknown error";
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
