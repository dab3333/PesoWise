package ph.pesowise.auth.web;

/**
 * Maps to 403 with the code {@code EMAIL_NOT_VERIFIED}.
 *
 * <p>403 rather than 401: the password was correct, so this is not an authentication failure —
 * treating it as one would make the frontend show "incorrect email or password" for an account
 * that simply has not been confirmed yet.
 *
 * <p>This is the one place account existence is revealed, and unavoidably so: the whole point is
 * to tell the user their account is waiting on confirmation. It only fires after a correct
 * password, so it discloses nothing to someone who does not already have the credentials.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException() {
        super("Confirm your email address before signing in. Check your inbox for the link.");
    }
}
