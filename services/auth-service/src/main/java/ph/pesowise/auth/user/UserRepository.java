package ph.pesowise.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** @param email must be normalised (lowercased) to match how rows are stored */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
