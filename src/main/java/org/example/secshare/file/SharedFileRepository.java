package org.example.secshare.file;

import org.example.secshare.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedFileRepository extends JpaRepository<SharedFile, UUID> {

    List<SharedFile> findByOwnerAndDeletedFalseOrderByCreatedAtDesc(User owner);

    /**
     * Case-insensitive filename search scoped to a single owner. The term is a bound parameter
     * (never concatenated), and LIKE wildcards in it are treated literally via {@code ESCAPE '\'}
     * — callers must pre-escape {@code %}, {@code _} and {@code \} in the raw term.
     */
    @Query("select f from SharedFile f "
            + "where f.owner = :owner and f.deleted = false "
            + "and lower(f.originalFilename) like lower(concat('%', :term, '%')) escape '\\' "
            + "order by f.createdAt desc")
    List<SharedFile> searchOwnedByFilename(@Param("owner") User owner, @Param("term") String term);

    List<SharedFile> findByDeletedFalseOrderByCreatedAtDesc();

    Optional<SharedFile> findByIdAndDeletedFalse(UUID id);
}