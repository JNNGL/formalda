package beer.sisipisi.formalda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "email_verification_codes")
public class EmailVerificationCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EmailVerificationCode() {
    }

    public EmailVerificationCode(User user, String codeHash, Instant expiresAt, Instant resendAvailableAt) {
        this.user = Objects.requireNonNull(user);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.resendAvailableAt = Objects.requireNonNull(resendAvailableAt);
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getResendAvailableAt() {
        return resendAvailableAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now) && attemptCount < 5;
    }

    public void registerFailedAttempt() {
        attemptCount++;
    }

    public void consume(Instant at) {
        consumedAt = Objects.requireNonNull(at);
    }
}
