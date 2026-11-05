package org.example.secshare.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AudienceMemberRepository extends JpaRepository<AudienceMember, UUID> {

    long countByAudience_Id(UUID audienceId);

    /**
     * Atomically stamps a member's first-open time, only if it has not opened before. The
     * {@code openedAt is null} guard means that of any concurrent opens for a member, exactly one
     * is counted as the first — the signal the ALL burn mode uses to advance its counter.
     *
     * @return 1 if this call was the member's first open, 0 otherwise.
     */
    @Modifying
    @Query("update AudienceMember m set m.openedAt = :now where m.id = :id and m.openedAt is null")
    int claimFirstOpen(@Param("id") UUID id, @Param("now") Instant now);

    /** Atomically bumps a member's download counter (every open, not just the first). */
    @Modifying
    @Query("update AudienceMember m set m.downloadCount = m.downloadCount + 1 where m.id = :id")
    void incrementDownloadCount(@Param("id") UUID id);

    /** True when {@code email} is a member of any of the given audiences (case-insensitive). */
    boolean existsByAudience_IdInAndEmailIgnoreCase(Collection<UUID> audienceIds, String email);

    Optional<AudienceMember> findByToken(String token);

    Optional<AudienceMember> findByAudience_IdAndEmailIgnoreCase(UUID audienceId, String email);

    List<AudienceMember> findByAudience_IdOrderByEmailAsc(UUID audienceId);
}
