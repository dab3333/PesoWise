package ph.pesowise.auth.web;

/**
 * Maps to 404. Reached when a token carries a subject whose user row no longer exists —
 * a deleted account holding a still-valid token.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("User not found.");
    }
}
