package ph.pesowise.auth.mail;

/**
 * How a composed message leaves the service.
 *
 * <p>Only delivery varies between environments — the message bodies are built once in
 * {@link AccountMailer}, so what a developer reads in the log is character-for-character what a
 * user receives.
 */
public interface MailDelivery {

    void send(String to, String subject, String body);
}
