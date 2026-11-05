package org.example.secshare.file;

import org.example.secshare.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileShareRepository extends JpaRepository<FileShare, UUID> {

    Optional<FileShare> findByTokenAndRevokedFalse(String token);

    /**
     * Atomically claims one download of a share link, closing the burn-after-reading race where
     * two simultaneous requests could both pass a {@code revoked = false} read and serve the file
     * twice. The single {@code UPDATE} takes a row-write lock, so concurrent callers serialize on
     * it; the {@code WHERE} guard rejects a claim once the link is revoked or its download limit is
     * spent, and the {@code CASE} flips {@code revoked} to true on the terminal download of a burn
     * link (first download when no limit is set, otherwise the one that reaches the limit).
     *
     * @return 1 if this caller claimed the download, 0 if the link was already spent/revoked.
     */
    @Modifying(clearAutomatically = true)
    @Query("update FileShare fs set fs.downloadCount = fs.downloadCount + 1, " +
            "fs.revoked = case when :burn = true and " +
            "(fs.maxDownloads is null or fs.downloadCount + 1 >= fs.maxDownloads) then true else fs.revoked end " +
            "where fs.id = :id and fs.revoked = false " +
            "and (fs.maxDownloads is null or fs.downloadCount < fs.maxDownloads)")
    int claimDownload(@Param("id") UUID id, @Param("burn") boolean burn);

    /**
     * Atomically claims the burn of a share, used by the audience path where the download counters
     * live on the member/audience rows rather than the share. The single {@code UPDATE} takes a
     * row-write lock so concurrent opens serialize on it; the {@code revoked = false} guard means
     * exactly one caller flips the share and the rest affect zero rows.
     *
     * @return 1 if this caller burned the share, 0 if it was already revoked.
     */
    @Modifying
    @Query("update FileShare fs set fs.revoked = true where fs.id = :id and fs.revoked = false")
    int claimRevoke(@Param("id") UUID id);

    List<FileShare> findByFileAndRevokedFalseOrderByCreatedAtDesc(SharedFile file);

    List<FileShare> findByRecipientAndTypeAndRevokedFalseOrderByCreatedAtDesc(User recipient, ShareType type);

    boolean existsByFileAndRecipientAndRevokedFalse(SharedFile file, User recipient);

    boolean existsByFile_IdAndRecipient_IdAndRevokedFalse(UUID fileId, UUID recipientId);

    Optional<FileShare> findByFile_IdAndRecipient_IdAndRevokedFalse(UUID fileId, UUID recipientId);

    List<FileShare> findByFile_IdAndTypeAndRevokedFalse(UUID fileId, ShareType type);

    /** Live burn-after-reading links — candidates the reaper inspects for expiry. */
    List<FileShare> findByBurnAfterAccessTrueAndRevokedFalse();

    /** The active share that exposes a given audience (audiences map 1:1 to a share). */
    Optional<FileShare> findFirstByAudienceAndTypeAndRevokedFalse(Audience audience, ShareType type);

    /** Active audience shares whose audience includes the given recipient email. */
    @Query("select distinct fs from FileShare fs join fs.audience a " +
            "where fs.type = :type and fs.revoked = false and exists " +
            "(select 1 from AudienceMember m where m.audience = a and lower(m.email) = lower(:email))")
    List<FileShare> findAudienceSharesForEmail(@Param("email") String email, @Param("type") ShareType type);
}
