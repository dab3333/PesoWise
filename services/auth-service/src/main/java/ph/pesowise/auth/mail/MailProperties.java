package ph.pesowise.auth.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pesowise.mail")
public class MailProperties {

    /**
     * Whether to actually deliver mail. Off by default so the stack runs with no SMTP provider:
     * links are logged instead and registrations self-verify. Must be on in any deployment
     * reachable by someone other than the developer.
     */
    private boolean enabled = false;

    /**
     * Whether a new account must confirm its address before it can sign in.
     *
     * <p>Unset by default, in which case it follows {@link #enabled} — no delivery, no
     * confirmation requirement, so a stack with no SMTP account stays usable.
     *
     * <p>Setting it independently is what makes the flow testable without a mail provider: with
     * {@code enabled=false} and this {@code true}, confirmation is genuinely required and the
     * link is written to the log, so the whole journey can be walked through in a browser. The
     * two are separate knobs because they answer different questions — "can we send mail" and
     * "do we demand proof of the address".
     */
    private Boolean requireVerification;

    /** Envelope sender. With most free tiers this address has to be verified with the provider. */
    private String from = "no-reply@pesowise.app";

    private String fromName = "PesoWise";

    /**
     * Public origin of the frontend, used to build the links in emails. Not derived from the
     * request: the service sits behind a gateway and an nginx proxy, so the inbound Host header
     * is not a trustworthy basis for a link that grants account access.
     */
    private String publicUrl = "http://localhost:3000";

    /** How long a verification link stays valid. */
    private long verificationExpiryMinutes = 24 * 60;

    /** Shorter than verification: a leaked reset link is an immediate takeover. */
    private long resetExpiryMinutes = 60;

    /** Minimum gap between verification emails to the same account. */
    private long resendCooldownSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Defaults to {@link #enabled} when not set explicitly. */
    public boolean isVerificationRequired() {
        return requireVerification != null ? requireVerification : enabled;
    }

    public Boolean getRequireVerification() {
        return requireVerification;
    }

    public void setRequireVerification(Boolean requireVerification) {
        this.requireVerification = requireVerification;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public long getVerificationExpiryMinutes() {
        return verificationExpiryMinutes;
    }

    public void setVerificationExpiryMinutes(long verificationExpiryMinutes) {
        this.verificationExpiryMinutes = verificationExpiryMinutes;
    }

    public long getResetExpiryMinutes() {
        return resetExpiryMinutes;
    }

    public void setResetExpiryMinutes(long resetExpiryMinutes) {
        this.resetExpiryMinutes = resetExpiryMinutes;
    }

    public long getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public void setResendCooldownSeconds(long resendCooldownSeconds) {
        this.resendCooldownSeconds = resendCooldownSeconds;
    }
}
