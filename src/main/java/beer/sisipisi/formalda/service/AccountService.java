package beer.sisipisi.formalda.service;

import beer.sisipisi.formalda.config.FormaldaProperties;
import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.entity.EmailVerificationCode;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.repository.EmailVerificationCodeRepository;
import beer.sisipisi.formalda.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Service
public class AccountService {
    private static final Duration DEFAULT_CODE_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final UserRepository users;
    private final EmailVerificationCodeRepository verificationCodes;
    private final PasswordEncoder passwordEncoder;
    private final VerificationMailService mailService;
    private final FormaldaProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public AccountService(
            UserRepository users,
            EmailVerificationCodeRepository verificationCodes,
            PasswordEncoder passwordEncoder,
            VerificationMailService mailService,
            FormaldaProperties properties,
            SecureRandom secureRandom,
            Clock clock) {
        this.users = users;
        this.verificationCodes = verificationCodes;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResult register(Forms.Registration form, Locale locale) {
        Objects.requireNonNull(form, "form");
        String email = normalizeEmail(form.getEmail());
        String username = normalizeUsername(form.getUsername());

        if (!Objects.equals(form.getPassword(), form.getPasswordConfirmation())) {
            throw new RegistrationException(RegistrationProblem.PASSWORD_MISMATCH);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new RegistrationException(RegistrationProblem.EMAIL_TAKEN);
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new RegistrationException(RegistrationProblem.USERNAME_TAKEN);
        }

        User user = new User(email, username, passwordEncoder.encode(form.getPassword()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new RegistrationException(RegistrationProblem.ACCOUNT_CONFLICT, exception);
        }

        String code = createVerificationCode(user);
        mailService.send(user, code, locale == null ? Locale.ROOT : locale);
        return new RegistrationResult(user.getEmail());
    }

    @Transactional
    public VerificationResult verify(String rawEmail, String rawCode) {
        String email = normalizeEmail(rawEmail);
        String code = rawCode == null ? "" : rawCode.strip();
        User user = users.findByEmailIgnoreCaseForUpdate(email).orElse(null);
        if (user == null) {
            return VerificationResult.INVALID;
        }
        if (user.isEmailVerified()) {
            return VerificationResult.ALREADY_VERIFIED;
        }

        EmailVerificationCode verificationCode = verificationCodes
                .findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(user)
                .orElse(null);
        if (verificationCode == null) {
            return VerificationResult.INVALID;
        }

        Instant now = clock.instant();
        if (verificationCode.getAttemptCount() >= maxAttempts()) {
            return VerificationResult.ATTEMPTS_EXCEEDED;
        }
        if (!verificationCode.getExpiresAt().isAfter(now)) {
            return VerificationResult.EXPIRED;
        }
        if (!passwordEncoder.matches(code, verificationCode.getCodeHash())) {
            verificationCode.registerFailedAttempt();
            verificationCodes.save(verificationCode);
            return verificationCode.getAttemptCount() >= maxAttempts()
                    ? VerificationResult.ATTEMPTS_EXCEEDED
                    : VerificationResult.INVALID;
        }

        verificationCode.consume(now);
        user.verifyEmail();
        verificationCodes.save(verificationCode);
        users.save(user);
        return VerificationResult.VERIFIED;
    }

    @Transactional
    public ResendResult resend(String rawEmail, Locale locale) {
        String email = normalizeEmail(rawEmail);
        User user = users.findByEmailIgnoreCaseForUpdate(email).orElse(null);
        if (user == null) {
            return ResendResult.sent();
        }
        if (user.isEmailVerified()) {
            return ResendResult.alreadyVerified();
        }

        Instant now = clock.instant();
        EmailVerificationCode current = verificationCodes
                .findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(user)
                .orElse(null);
        if (current != null && current.getResendAvailableAt().isAfter(now)) {
            return ResendResult.tooSoon(Duration.between(now, current.getResendAvailableAt()));
        }
        if (current != null) {
            current.consume(now);
            verificationCodes.save(current);
        }

        String code = createVerificationCode(user);
        mailService.send(user, code, locale == null ? Locale.ROOT : locale);
        return ResendResult.sent();
    }

    private String createVerificationCode(User user) {
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        Instant now = clock.instant();
        EmailVerificationCode verificationCode = new EmailVerificationCode(
                user,
                passwordEncoder.encode(code),
                now.plus(codeTtl()),
                now.plus(resendCooldown()));
        verificationCodes.saveAndFlush(verificationCode);
        return code;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.strip();
    }

    private Duration codeTtl() {
        FormaldaProperties.Verification verification = properties.verification();
        return verification == null || verification.ttl() == null
                ? DEFAULT_CODE_TTL
                : verification.ttl();
    }

    private Duration resendCooldown() {
        FormaldaProperties.Verification verification = properties.verification();
        return verification == null || verification.resendCooldown() == null
                ? DEFAULT_RESEND_COOLDOWN
                : verification.resendCooldown();
    }

    private int maxAttempts() {
        FormaldaProperties.Verification verification = properties.verification();
        return verification == null || verification.maxAttempts() <= 0
                ? DEFAULT_MAX_ATTEMPTS
                : verification.maxAttempts();
    }

    public record RegistrationResult(String email) {
    }

    public enum VerificationResult {
        VERIFIED,
        ALREADY_VERIFIED,
        INVALID,
        EXPIRED,
        ATTEMPTS_EXCEEDED
    }

    public record ResendResult(ResendStatus status, Duration retryAfter) {
        public static ResendResult sent() {
            return new ResendResult(ResendStatus.SENT, Duration.ZERO);
        }

        public static ResendResult alreadyVerified() {
            return new ResendResult(ResendStatus.ALREADY_VERIFIED, Duration.ZERO);
        }

        public static ResendResult tooSoon(Duration retryAfter) {
            return new ResendResult(ResendStatus.TOO_SOON, retryAfter);
        }
    }

    public enum ResendStatus {
        SENT,
        ALREADY_VERIFIED,
        TOO_SOON
    }

    public enum RegistrationProblem {
        EMAIL_TAKEN,
        USERNAME_TAKEN,
        PASSWORD_MISMATCH,
        ACCOUNT_CONFLICT
    }

    public static final class RegistrationException extends RuntimeException {
        private final RegistrationProblem problem;

        public RegistrationException(RegistrationProblem problem) {
            super(problem.name());
            this.problem = problem;
        }

        public RegistrationException(RegistrationProblem problem, Throwable cause) {
            super(problem.name(), cause);
            this.problem = problem;
        }

        public RegistrationProblem getProblem() {
            return problem;
        }
    }
}
