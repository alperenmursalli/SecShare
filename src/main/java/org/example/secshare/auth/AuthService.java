package org.example.secshare.auth;

import org.example.secshare.auth.dto.RegisterRequest;
import org.example.secshare.auth.dto.LoginRequest;
import org.example.secshare.auth.dto.AuthResponse;
import org.example.secshare.auth.dto.MeResponse;
import org.example.secshare.auth.security.UserPrincipal;
import org.example.secshare.mail.EmailOutboxService;
import org.example.secshare.user.User;
import org.example.secshare.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    /** How long a verification link stays valid before the user must request a fresh one. */
    private static final long VERIFICATION_TTL_HOURS = 24;
    private static final int VERIFICATION_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailOutboxService emailOutboxService;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailOutboxService emailOutboxService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailOutboxService = emailOutboxService;
    }

    public void register(RegisterRequest request) {

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles("USER");
        user.setCreatedAt(Instant.now());
        user.setEmailVerified(false);
        assignVerificationToken(user);

        userRepository.save(user);

        sendVerificationEmail(user);
    }

    /**
     * Marks an account's email as verified, given a valid, unexpired verification token.
     * Idempotent only up to token lifetime: the token is cleared on success so a link works once.
     */
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token is required");
        }

        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification link"));

        if (user.getVerificationExpiresAt() == null
                || Instant.now().isAfter(user.getVerificationExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification link");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationExpiresAt(null);
        userRepository.save(user);
    }

    /** Issues a fresh verification link for the signed-in user (e.g. the first email never arrived). */
    public void resendVerification(UserPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already verified");
        }

        assignVerificationToken(user);
        userRepository.save(user);
        sendVerificationEmail(user);
    }

    private void assignVerificationToken(User user) {
        byte[] bytes = new byte[VERIFICATION_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        user.setVerificationToken(tokenEncoder.encodeToString(bytes));
        user.setVerificationExpiresAt(Instant.now().plus(VERIFICATION_TTL_HOURS, ChronoUnit.HOURS));
    }

    /**
     * Enqueues the verification email. Requires mail to be configured (enabled + a public base
     * URL so the link is absolute); when it is not — local dev, tests — nothing is sent and the
     * token simply waits in the database for an operator-driven or test-driven verification.
     */
    private void sendVerificationEmail(User user) {
        if (!emailOutboxService.isConfigured()) {
            return;
        }
        String link = emailOutboxService.publicBaseUrl() + "/api/auth/verify?token=" + user.getVerificationToken();
        String subject = "Verify your SecShare email address";
        String body = "Hello,\n\n"
                + "Confirm this email address to start receiving files shared with you on SecShare:\n"
                + link + "\n\n"
                + "The link expires in " + VERIFICATION_TTL_HOURS + " hours. "
                + "If you did not create a SecShare account, you can ignore this message.";
        emailOutboxService.enqueue(user.getEmail(), subject, body);
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                parseRoles(user)
        );

        return new AuthResponse(token);
    }

    public MeResponse me(UserPrincipal principal) {

        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                parseRoles(user),
                user.getCreatedAt(),
                user.isEmailVerified()
        );
    }

    /** Turns the comma-separated roles column into a clean list, defaulting to USER. */
    private List<String> parseRoles(User user) {
        if (user.getRoles() == null || user.getRoles().isBlank()) {
            return List.of("USER");
        }
        List<String> roles = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(r -> !r.isBlank())
                .toList();
        return roles.isEmpty() ? List.of("USER") : roles;
    }
}