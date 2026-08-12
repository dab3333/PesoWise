package ph.pesowise.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.auth.user.User;
import ph.pesowise.auth.user.UserRepository;

import java.util.Set;

/**
 * Promotes the configured addresses to ADMIN at startup.
 *
 * <p>Together with the same check in registration, this makes the order of events irrelevant:
 * an address listed before the account exists is promoted when it registers, and one listed
 * afterwards is promoted on the next restart. Neither path needs a seeded password or a manual
 * {@code UPDATE}.
 *
 * <p>Promotion only. An address disappearing from the list does not demote anyone — a typo in an
 * environment variable should not quietly remove someone's access, and demotion belongs in the
 * admin panel where it leaves an audit trail.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final AdminProperties properties;

    public AdminBootstrap(UserRepository users, AdminProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> emails = properties.normalisedEmails();
        if (emails.isEmpty()) {
            log.info("No pesowise.admin.emails configured — no administrator will exist "
                    + "until one is set.");
            return;
        }

        for (String email : emails) {
            users.findByEmail(email).ifPresentOrElse(
                    user -> {
                        if (user.getRole() == User.Role.ADMIN) {
                            return;
                        }
                        user.setRole(User.Role.ADMIN);
                        log.info("Promoted {} to ADMIN", email);
                    },
                    () -> log.info("Admin address {} has no account yet — it will be promoted "
                            + "when it registers.", email));
        }
    }
}
