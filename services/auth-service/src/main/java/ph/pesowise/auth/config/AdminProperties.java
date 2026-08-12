package ph.pesowise.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who gets the ADMIN role.
 *
 * <p>Configuration rather than data, so a fresh deployment has an administrator without a seeded
 * password, a migration carrying a hard-coded account, or a manual SQL step. The listed addresses
 * are promoted at startup if they already exist, and on registration if they do not — which means
 * the order of "deploy" and "sign up" does not matter.
 *
 * <p>The database column stays the source of truth: removing an address here does not demote
 * anyone. Demotion is an explicit admin action, so that a typo in an environment variable cannot
 * silently strip access.
 */
@ConfigurationProperties(prefix = "pesowise.admin")
public class AdminProperties {

    private List<String> emails = List.of();

    /** Normalised the same way {@code users.email} is stored, so comparison is a plain equals. */
    public Set<String> normalisedEmails() {
        return emails.stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails;
    }
}
