package ph.pesowise.auth.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ph.pesowise.auth.admin.SignupDayRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** @param email must be normalised (lowercased) to match how rows are stored */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * The admin user list. Deliberately not scoped by user — this is the one query in the whole
     * service allowed to see every account, and it exists only behind {@code /internal/admin/**},
     * which the gateway does not route.
     *
     * <p>{@code :q} is cast explicitly: with a null parameter feeding straight into {@code
     * LOWER(...)}, Postgres cannot infer the placeholder's type from context and defaults to
     * {@code bytea}, which then fails with "function lower(bytea) does not exist" the moment the
     * search box is left empty. The cast pins the type regardless of the value.
     */
    @Query("""
            SELECT u FROM User u
            WHERE :q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                             OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    long countByEmailVerifiedTrue();

    long countByDisabledTrue();

    long countByRole(User.Role role);

    /** One row per day with at least one signup in the last 30 days; the caller fills gaps. */
    @Query(value = """
            SELECT created_at::date AS day, count(*) AS count
            FROM users
            WHERE created_at >= now() - interval '30 days'
            GROUP BY created_at::date
            ORDER BY day
            """, nativeQuery = true)
    List<SignupDayRow> countSignupsPerDay();
}
