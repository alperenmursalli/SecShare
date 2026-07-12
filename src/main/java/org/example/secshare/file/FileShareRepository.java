package org.example.secshare.file;

import org.example.secshare.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileShareRepository extends JpaRepository<FileShare, UUID> {

    Optional<FileShare> findByTokenAndRevokedFalse(String token);

    List<FileShare> findByFileAndRevokedFalseOrderByCreatedAtDesc(SharedFile file);

    List<FileShare> findByRecipientAndTypeAndRevokedFalseOrderByCreatedAtDesc(User recipient, ShareType type);

    boolean existsByFileAndRecipientAndRevokedFalse(SharedFile file, User recipient);

    boolean existsByFile_IdAndRecipient_IdAndRevokedFalse(UUID fileId, UUID recipientId);

    /** Live burn-after-reading links — candidates the reaper inspects for expiry. */
    List<FileShare> findByBurnAfterAccessTrueAndRevokedFalse();
}
