package ph.pesowise.auth.web;

/**
 * Maps to 401. The message is deliberately vague about whether the email or the password
 * was wrong — saying which would let anyone enumerate registered accounts.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Incorrect email or password.");
    }
}
