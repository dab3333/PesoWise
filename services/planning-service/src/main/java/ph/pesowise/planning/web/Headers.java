package ph.pesowise.planning.web;

public final class Headers {

    /**
     * Injected by the gateway after it verifies the JWT. Any client-supplied copy is stripped
     * there, so this value is trustworthy — and requests missing it never came through the
     * gateway, which {@link ApiExceptionHandler} turns into a 401.
     */
    public static final String USER_ID = "X-User-Id";

    /**
     * The caller's role, injected by the gateway alongside {@link #USER_ID} and stripped there
     * if a client supplies its own. The gateway already refuses non-admins on {@code
     * /api/admin/**}; this exists for the handful of endpoints that live outside that prefix but
     * still must not be open to everyone.
     */
    public static final String USER_ROLE = "X-User-Role";

    public static final String ADMIN_ROLE = "ADMIN";

    private Headers() {
    }
}
