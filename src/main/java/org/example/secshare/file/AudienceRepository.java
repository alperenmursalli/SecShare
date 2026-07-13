package org.example.secshare.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AudienceRepository extends JpaRepository<Audience, UUID> {
}
