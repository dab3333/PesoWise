package ph.pesowise.auth.web;

/** Maps to 409 Conflict. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("That email is already registered.");
    }
}
