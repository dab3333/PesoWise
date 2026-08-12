package ph.pesowise.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes the message to the log instead of sending it.
 *
 * <p>This is what makes the whole verification and reset flow developable and testable without
 * an SMTP account. The link is printed in full so it can be pasted straight into a browser.
 *
 * <p>Logged at WARN, not INFO, and with an explicit banner: these lines contain live
 * account-access tokens. Seeing them in a log should feel wrong, because in a real deployment
 * it would be.
 */
@Component
@ConditionalOnProperty(name = "pesowise.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailDelivery implements MailDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailDelivery.class);

    public LoggingMailDelivery() {
        log.warn("Mail delivery is DISABLED. Verification and reset links will be logged, not sent. "
                + "Set pesowise.mail.enabled=true with SMTP credentials before deploying.");
    }

    @Override
    public void send(String to, String subject, String body) {
        log.warn("""

                ---------------- MAIL NOT SENT (delivery disabled) ----------------
                To:      {}
                Subject: {}

                {}
                ------------------------------------------------------------------
                """, to, subject, body);
    }
}
