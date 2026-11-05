package org.example.secshare.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AudienceRepository extends JpaRepository<Audience, UUID> {

    /**
     * Atomically advances the count of distinct members that have opened the audience's share.
     * The {@code UPDATE} takes a row-write lock, so concurrent first-opens serialize on it and no
     * increment is lost — the ALL burn mode relies on this count reaching {@code memberCount}.
     */
    @Modifying
    @Query("update Audience a set a.openedCount = a.openedCount + 1 where a.id = :id")
    void incrementOpenedCount(@Param("id") UUID id);

    /** Reads the live opened-count straight from the row (bypasses the stale first-level cache). */
    @Query("select a.openedCount from Audience a where a.id = :id")
    int currentOpenedCount(@Param("id") UUID id);
}
