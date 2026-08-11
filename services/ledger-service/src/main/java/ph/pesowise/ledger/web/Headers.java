package ph.pesowise.ledger.web;

public final class Headers {

    /**
     * Injected by the gateway after it verifies the JWT. Any client-supplied copy is stripped
     * there, so this value is trustworthy — and requests missing it never came through the
     * gateway, which {@link ApiExceptionHandler} turns into a 401.
     */
    public static final String USER_ID = "X-User-Id";

    private Headers() {
    }
}
