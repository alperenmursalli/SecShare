package org.example.secshare.mail;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    /** Oldest pending mail first; {@code Pageable} bounds the batch (rate limiting). */
    List<EmailOutbox> findByStatusOrderByCreatedAtAsc(EmailStatus status, Pageable pageable);
}
