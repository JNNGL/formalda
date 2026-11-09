package beer.sisipisi.formalda.service;

import beer.sisipisi.formalda.config.FormaldaProperties;
import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.entity.EmailVerificationCode;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.repository.EmailVerificationCodeRepository;
import beer.sisipisi.formalda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");

    @Mock
    private UserRepository users;
    @Mock
    private EmailVerificationCodeRepository verificationCodes;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private VerificationMailService mailService;
    @Mock
    private SecureRandom secureRandom;

    private AccountService accounts;

    @BeforeEach
    void setUp() {
        FormaldaProperties properties = new FormaldaProperties(
                "http://localhost:8080",
                new FormaldaProperties.Verification(Duration.ofMinutes(10), 5, Duration.ofSeconds(60)),
                new FormaldaProperties.BootstrapAdmin("", "", ""));
        accounts = new AccountService(
                users,
                verificationCodes,
                passwordEncoder,
                mailService,
                properties,
                secureRandom,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registrationNormalizesIdentityHashesSecretsAndSendsSixDigitCode() {
        Forms.Registration form = registrationForm();
        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("Person.Name")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(secureRandom.nextInt(1_000_000)).thenReturn(42);
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationCodes.saveAndFlush(any(EmailVerificationCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountService.RegistrationResult result = accounts.register(form, Locale.ENGLISH);

        assertThat(result.email()).isEqualTo("person@example.com");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("person@example.com");
        assertThat(savedUser.getUsername()).isEqualTo("Person.Name");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hash:correct horse");
        assertThat(savedUser.isEmailVerified()).isFalse();

        ArgumentCaptor<EmailVerificationCode> codeCaptor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(verificationCodes).saveAndFlush(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getCodeHash()).isEqualTo("hash:000042");
        assertThat(codeCaptor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(codeCaptor.getValue().getResendAvailableAt()).isEqualTo(NOW.plusSeconds(60));
        verify(mailService).send(savedUser, "000042", Locale.ENGLISH);
    }

    @Test
    void registrationRejectsMismatchingPasswordsBeforeWriting() {
        Forms.Registration form = registrationForm();
        form.setPasswordConfirmation("different password");

        assertThatThrownBy(() -> accounts.register(form, Locale.ENGLISH))
                .isInstanceOf(AccountService.RegistrationException.class)
                .extracting(exception -> ((AccountService.RegistrationException) exception).getProblem())
                .isEqualTo(AccountService.RegistrationProblem.PASSWORD_MISMATCH);
        verify(users, never()).saveAndFlush(any());
        verify(mailService, never()).send(any(), anyString(), any());
    }

    @Test
    void fifthWrongCodeLocksVerificationAndPersistsEveryAttempt() {
        User user = new User("person@example.com", "person", "password-hash");
        EmailVerificationCode code = new EmailVerificationCode(
                user,
                "code-hash",
                NOW.plusSeconds(600),
                NOW.plusSeconds(60));
        when(users.findByEmailIgnoreCaseForUpdate("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodes.findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("111111", "code-hash")).thenReturn(false);

        AccountService.VerificationResult result = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            result = accounts.verify("person@example.com", "111111");
        }

        assertThat(result).isEqualTo(AccountService.VerificationResult.ATTEMPTS_EXCEEDED);
        assertThat(code.getAttemptCount()).isEqualTo(5);
        verify(verificationCodes, org.mockito.Mockito.times(5)).save(code);
        verify(users, never()).save(any());
    }

    @Test
    void validCodeVerifiesUserAndConsumesCode() {
        User user = new User("person@example.com", "person", "password-hash");
        EmailVerificationCode code = new EmailVerificationCode(
                user,
                "code-hash",
                NOW.plusSeconds(600),
                NOW.plusSeconds(60));
        when(users.findByEmailIgnoreCaseForUpdate("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodes.findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);

        AccountService.VerificationResult result = accounts.verify(" PERSON@EXAMPLE.COM ", "123456");

        assertThat(result).isEqualTo(AccountService.VerificationResult.VERIFIED);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(code.getConsumedAt()).isEqualTo(NOW);
        verify(users).save(user);
        verify(verificationCodes).save(code);
    }

    private Forms.Registration registrationForm() {
        Forms.Registration form = new Forms.Registration();
        form.setEmail(" Person@Example.COM ");
        form.setUsername(" Person.Name ");
        form.setPassword("correct horse");
        form.setPasswordConfirmation("correct horse");
        return form;
    }
}
