package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.EmailVerificationCode;
import beer.sisipisi.formalda.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(User user);

    Optional<EmailVerificationCode> findFirstByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, Instant now);

    Optional<EmailVerificationCode> findFirstByUserEmailIgnoreCaseAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant now);

    Optional<EmailVerificationCode> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationCode code
               set code.consumedAt = :now, code.updatedAt = :now
             where code.user.id = :userId and code.consumedAt is null
            """)
    int consumeActiveCodes(@Param("userId") Long userId, @Param("now") Instant now);

    long deleteByUserId(Long userId);

    long deleteByUser(User user);

    long deleteByExpiresAtBefore(Instant threshold);
}
