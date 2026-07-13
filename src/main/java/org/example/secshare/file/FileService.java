package org.example.secshare.file;

import org.example.secshare.auth.security.UserPrincipal;
import org.example.secshare.file.dto.FileInfoResponse;
import org.example.secshare.file.scan.FileScanService;
import org.example.secshare.file.scan.ScanResult;
import org.example.secshare.file.scan.ScanStatus;
import org.example.secshare.user.User;
import org.example.secshare.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "txt", "doc", "docx", "xlsx", "zip"
    );

    private final SharedFileRepository sharedFileRepository;
    private final FileShareRepository fileShareRepository;
    private final AudienceMemberRepository audienceMemberRepository;
    private final UserRepository userRepository;
    private final FileScanService fileScanService;
    private final Path baseStoragePath;

    public FileService(
            SharedFileRepository sharedFileRepository,
            FileShareRepository fileShareRepository,
            AudienceMemberRepository audienceMemberRepository,
            UserRepository userRepository,
            FileScanService fileScanService,
            @Value("${app.storage.base-path:/uploads}") String basePath
    ) {
        this.sharedFileRepository = sharedFileRepository;
        this.fileShareRepository = fileShareRepository;
        this.audienceMemberRepository = audienceMemberRepository;
        this.userRepository = userRepository;
        this.fileScanService = fileScanService;
        this.baseStoragePath = Paths.get(basePath).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.baseStoragePath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory: " + this.baseStoragePath, e);
        }
    }

    public FileInfoResponse upload(MultipartFile file, UserPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (max 50MB)");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "file";
        }

        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This file type is not allowed");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read upload");
        }

        // Scan before anything touches disk — an infected upload never gets persisted.
        ScanResult scan = fileScanService.scan(content, originalFilename);
        if (!scan.clean()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "File rejected by malware scan: " + scan.signature());
        }

        UUID fileId = UUID.randomUUID();
        String storageFilename = fileId + (extension.isEmpty() ? "" : "." + extension);

        Path targetPath = baseStoragePath.resolve(storageFilename).normalize();
        if (!targetPath.startsWith(baseStoragePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }

        try {
            Files.write(targetPath, content);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save file");
        }

        User owner = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        SharedFile sharedFile = new SharedFile();
        sharedFile.setId(fileId);
        sharedFile.setOwner(owner);
        sharedFile.setOriginalFilename(originalFilename);
        sharedFile.setStorageFilename(storageFilename);
        sharedFile.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        sharedFile.setSizeBytes(file.getSize());
        sharedFile.setCreatedAt(Instant.now());
        sharedFile.setDeleted(false);
        sharedFile.setScanStatus(ScanStatus.CLEAN);

        sharedFileRepository.save(sharedFile);

        return toResponse(sharedFile);
    }

    public List<FileInfoResponse> listMyFiles(UserPrincipal principal) {
        User owner = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        return sharedFileRepository.findByOwnerAndDeletedFalseOrderByCreatedAtDesc(owner)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Resource loadFile(UUID fileId, UserPrincipal principal) {
        SharedFile sharedFile = getAccessibleFileOrThrow(fileId, principal);
        return loadResource(sharedFile);
    }

    /**
     * Builds a readable {@link Resource} for the given file's bytes on disk. Performs no
     * authorization — callers must have already established access (ownership, a valid
     * grant, or a valid share link).
     */
    public Resource loadResource(SharedFile sharedFile) {
        Path filePath = baseStoragePath.resolve(sharedFile.getStorageFilename()).normalize();
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    /** Owner-only lookup — used for delete and share management. */
    public SharedFile getOwnedFileOrThrow(UUID fileId, UserPrincipal principal) {
        SharedFile sharedFile = sharedFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (!sharedFile.getOwner().getId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this file");
        }

        return sharedFile;
    }

    /**
     * Download-level access: the owner, a user the file has been granted to via an active
     * USER share, or a member of an audience the file has been shared with.
     */
    public SharedFile getAccessibleFileOrThrow(UUID fileId, UserPrincipal principal) {
        SharedFile sharedFile = sharedFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        boolean isOwner = sharedFile.getOwner().getId().equals(principal.userId());
        boolean isGranted = !isOwner && fileShareRepository
                .existsByFile_IdAndRecipient_IdAndRevokedFalse(fileId, principal.userId());

        boolean isAudienceMember = false;
        if (!isOwner && !isGranted) {
            List<UUID> audienceIds = fileShareRepository.findActiveAudienceIds(fileId, ShareType.AUDIENCE);
            isAudienceMember = !audienceIds.isEmpty() && audienceMemberRepository
                    .existsByAudience_IdInAndEmailIgnoreCase(audienceIds, principal.email());
        }

        if (!isOwner && !isGranted && !isAudienceMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this file");
        }

        return sharedFile;
    }

    public void delete(UUID fileId, UserPrincipal principal) {
        SharedFile sharedFile = getOwnedFileOrThrow(fileId, principal);
        sharedFile.setDeleted(true);
        sharedFileRepository.save(sharedFile);

        deleteBytesFromDisk(sharedFile);
    }

    /**
     * Reads the full bytes of a file from disk into memory. Used for burn-after-reading
     * downloads, where the physical file must be destroyed immediately after the bytes are
     * captured (so they can still be streamed to the client). Performs no authorization.
     */
    public byte[] readAllBytes(SharedFile sharedFile) {
        Path filePath = baseStoragePath.resolve(sharedFile.getStorageFilename()).normalize();
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.GONE, "This file is no longer available");
        }
    }

    /**
     * Irreversibly destroys a file: deletes its bytes from disk and soft-deletes the record.
     * Used by the self-destruct paths (burn-after-reading download and the scheduled reaper).
     * Performs no authorization — callers must have already established the file should die.
     */
    public void purge(SharedFile sharedFile) {
        sharedFile.setDeleted(true);
        sharedFileRepository.save(sharedFile);
        deleteBytesFromDisk(sharedFile);
    }

    /** Wraps in-memory bytes as a downloadable resource (used after a burn). */
    public Resource asResource(byte[] content) {
        return new ByteArrayResource(content);
    }

    private void deleteBytesFromDisk(SharedFile sharedFile) {
        Path filePath = baseStoragePath.resolve(sharedFile.getStorageFilename()).normalize();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }

    private FileInfoResponse toResponse(SharedFile file) {
        return new FileInfoResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getSizeBytes(),
                file.getContentType(),
                file.getCreatedAt(),
                file.getScanStatus() != null ? file.getScanStatus().name() : null
        );
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
    
    public List<FileInfoResponse> listAllFiles() {
        return sharedFileRepository.findByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }
}