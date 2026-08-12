package ph.pesowise.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Mints and hashes the one-time tokens that go into verification and reset links.
 *
 * <p>Two deliberate choices:
 *
 * <ul>
 *   <li><b>32 bytes from {@link SecureRandom}</b>, URL-safe Base64 encoded. The token is the
 *       entire secret — there is no accompanying password to fall back on — so it has to be
 *       wide enough that guessing is hopeless.</li>
 *   <li><b>Plain SHA-256 for storage, not BCrypt.</b> Password hashing is deliberately slow to
 *       survive being brute-forced against a low-entropy secret. A 256-bit random token has no
 *       such weakness, so the slowness buys nothing and would instead make redemption lookups
 *       impossible: BCrypt salts every hash, so the same token would hash differently each time
 *       and could not be found by index. SHA-256 is deterministic, which is what lets
 *       {@code findByTokenHash} work at all.</li>
 * </ul>
 */
public final class TokenValues {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenValues() {
    }

    /** The value that goes in the email. Never stored. */
    public static String mint() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The value that goes in the database: 64 lowercase hex characters. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK; if it is missing the platform is broken.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
