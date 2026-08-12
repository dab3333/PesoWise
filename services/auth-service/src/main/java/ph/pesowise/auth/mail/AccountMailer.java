package ph.pesowise.auth.mail;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the two account emails PesoWise sends.
 *
 * <p>Composition lives here rather than in the delivery implementations so both environments
 * produce identical text — the link a developer copies out of the log is exactly the link a user
 * would receive.
 */
@Component
public class AccountMailer {

    private final MailDelivery delivery;
    private final MailProperties properties;

    public AccountMailer(MailDelivery delivery, MailProperties properties) {
        this.delivery = delivery;
        this.properties = properties;
    }

    public void sendVerification(String to, String displayName, String rawToken) {
        String link = link("/verify-email", rawToken);
        long hours = properties.getVerificationExpiryMinutes() / 60;

        delivery.send(to, "Confirm your PesoWise account", """
                Hi %s,

                Confirm this address to finish setting up your PesoWise account:

                %s

                The link works for %d hours. If you did not sign up, ignore this email —
                no account can be used until it is confirmed.

                — PesoWise
                """.formatted(displayName, link, hours));
    }

    public void sendPasswordReset(String to, String displayName, String rawToken) {
        String link = link("/reset-password", rawToken);
        long minutes = properties.getResetExpiryMinutes();

        delivery.send(to, "Reset your PesoWise password", """
                Hi %s,

                Use this link to choose a new PesoWise password:

                %s

                The link works for %d minutes and can only be used once.

                If you did not ask for this, you can ignore it — your current password
                still works and nothing has changed.

                — PesoWise
                """.formatted(displayName, link, minutes));
    }

    /**
     * The token travels as a query parameter, never as a path segment. The gateway matches its
     * public path list exactly, so a token in the path would not match — and URL-encoding keeps
     * Base64's '-' and '_' intact regardless.
     */
    private String link(String path, String rawToken) {
        return "%s%s?token=%s".formatted(
                trimTrailingSlash(properties.getPublicUrl()),
                path,
                URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
