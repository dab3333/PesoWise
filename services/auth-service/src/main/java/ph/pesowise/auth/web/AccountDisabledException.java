package ph.pesowise.auth.web;

/**
 * Maps to 403 with the code {@code ACCOUNT_DISABLED}.
 *
 * <p>Like {@link EmailNotVerifiedException}, raised only after the password checks out, so it
 * tells an attacker nothing they did not already know. The message gives no reason — that is a
 * conversation for the administrator who disabled it, not an API response.
 */
public class AccountDisabledException extends RuntimeException {

    public static final String CODE = "ACCOUNT_DISABLED";

    public AccountDisabledException() {
        super("This account has been disabled.");
    }
}
