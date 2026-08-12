package ph.pesowise.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real delivery over SMTP.
 *
 * <p>Plain text, no HTML. A verification mail is one sentence and one link; HTML would add a
 * template engine and a second body to keep in sync, and plain text is what actually survives
 * every client and spam filter intact.
 */
@Component
@ConditionalOnProperty(name = "pesowise.mail.enabled", havingValue = "true")
public class SmtpMailDelivery implements MailDelivery {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailDelivery.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public SmtpMailDelivery(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("%s <%s>".formatted(properties.getFromName(), properties.getFrom()));
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Sent \"{}\"", subject);
        } catch (MailException e) {
            // Deliberately swallowed. Every caller is in the middle of a flow whose response must
            // not reveal whether an address exists, and a provider outage should not fail a
            // registration that otherwise succeeded — the user can ask for another link.
            log.error("Failed to send \"{}\": {}", subject, e.getMessage());
        }
    }
}
