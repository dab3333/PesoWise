package ph.pesowise.auth.web;

/**
 * Maps to 400. Covers every way a verification or reset link can fail to work — unknown,
 * expired, or already redeemed — with one message.
 *
 * <p>Distinguishing them would tell a caller holding a guessed token that it was merely expired
 * rather than wrong, which is a hint worth denying. The user-facing remedy is the same in all
 * three cases: ask for a new link.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public static InvalidTokenException verification() {
        return new InvalidTokenException(
                "This confirmation link is no longer valid. Request a new one to continue.");
    }

    public static InvalidTokenException reset() {
        return new InvalidTokenException(
                "This reset link is no longer valid. Request a new one to continue.");
    }
}
