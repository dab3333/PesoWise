package ph.pesowise.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(UUID userId);

    /**
     * Spends every outstanding reset token for a user in one statement.
     *
     * <p>Called after a successful reset: if someone requested several links, the ones they did
     * not use must not stay live against the new password. Also called after a password change
     * for the same reason.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    int consumeAllForUser(UUID userId, Instant now);
}
