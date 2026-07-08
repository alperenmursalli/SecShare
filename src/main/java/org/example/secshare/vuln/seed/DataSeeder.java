package org.example.secshare.vuln.seed;

import org.example.secshare.file.SharedFile;
import org.example.secshare.file.SharedFileRepository;
import org.example.secshare.user.User;
import org.example.secshare.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * Vuln profili icin sabit hedef verisi olusturur: kurban kullanicilar ve
 * ele gecirilecek "flag" dosyalari. Sadece "vuln" profilinde calisir.
 *
 * Saldiri zinciri hedefleri:
 *  - alice / bob : zayif sifreli USER hesaplari (brute force -> gecerli token)
 *  - admin       : guclu sifre, sadece JWT forge ile erisilir (privesc)
 *  - flag dosyalari: IDOR / SQLi / RCE asamalarinin odulu
 */
@Component
@Profile("vuln")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final SharedFileRepository sharedFileRepository;
    private final PasswordEncoder passwordEncoder;
    private final Path baseStoragePath;

    public DataSeeder(UserRepository userRepository,
                      SharedFileRepository sharedFileRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.storage.base-path:uploads}") String basePath) {
        this.userRepository = userRepository;
        this.sharedFileRepository = sharedFileRepository;
        this.passwordEncoder = passwordEncoder;
        this.baseStoragePath = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            log.info("[seed] kullanici mevcut, seed atlaniyor");
            return;
        }
        Files.createDirectories(baseStoragePath);

        User admin = createUser("admin@secshare.local", "Str0ng-Adm!n-P@ss-2026", "ADMIN");
        User alice = createUser("alice@corp.local", "Summer2024!", "USER");
        User bob   = createUser("bob@corp.local", "password123", "USER");

        seedFile(admin, "master-keys.txt", "text/plain",
                "SecShare master key vault\n" +
                "root-recovery-code: FLAG{rce_all_the_way_down}\n");

        seedFile(alice, "salary.pdf", "application/pdf",
                "CONFIDENTIAL payroll 2026\nalice net: 184000\nFLAG{idor_reads_others_files}\n");

        seedFile(bob, "db-backup.txt", "text/plain",
                "# internal postgres dump config\n" +
                "DB_HOST=169.254.169.254\n" +
                "DB_USER=secshare\nDB_PASS=S3cr3t-db-pw\n" +
                "note: metadata endpoint http://169.254.169.254/latest/meta-data/ dahili\n" +
                "FLAG{ssrf_pivot_starts_here}\n");

        log.warn("[seed] VULN hedef verisi olusturuldu: admin/alice/bob + flag dosyalari");
    }

    private User createUser(String email, String rawPassword, String roles) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setRoles(roles);
        u.setCreatedAt(Instant.now());
        return userRepository.save(u);
    }

    private void seedFile(User owner, String originalFilename, String contentType, String content)
            throws Exception {
        UUID fileId = UUID.randomUUID();
        int dot = originalFilename.lastIndexOf('.');
        String ext = dot == -1 ? "" : originalFilename.substring(dot + 1);
        String storageFilename = fileId + (ext.isEmpty() ? "" : "." + ext);

        Path target = baseStoragePath.resolve(storageFilename).normalize();
        Files.writeString(target, content, StandardCharsets.UTF_8);

        SharedFile f = new SharedFile();
        f.setId(fileId);
        f.setOwner(owner);
        f.setOriginalFilename(originalFilename);
        f.setStorageFilename(storageFilename);
        f.setContentType(contentType);
        f.setSizeBytes(content.getBytes(StandardCharsets.UTF_8).length);
        f.setCreatedAt(Instant.now());
        f.setDeleted(false);
        sharedFileRepository.save(f);
    }
}
