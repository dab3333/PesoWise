package ph.pesowise.auth.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.auth.api.AuthDtos.AuthResponse;
import ph.pesowise.auth.api.AuthDtos.LoginRequest;
import ph.pesowise.auth.api.AuthDtos.RegisterRequest;
import ph.pesowise.auth.api.AuthDtos.UserResponse;
import ph.pesowise.auth.web.EmailAlreadyRegisteredException;
import ph.pesowise.auth.web.InvalidCredentialsException;
import ph.pesowise.auth.web.UserNotFoundException;
import ph.pesowise.auth.user.User;
import ph.pesowise.auth.user.UserRepository;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());

        // Checked up front for a clean 409, but the unique index below is the real guard —
        // two concurrent registrations would both pass this check.
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.create(email, passwordEncoder.encode(request.password()), request.displayName().trim());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }

        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalise(request.email()))
                .orElse(null);

        // Hash a throwaway value when the user is absent so a missing account and a wrong
        // password take comparable time — otherwise response latency reveals which emails
        // are registered.
        if (user == null) {
            passwordEncoder.encode(request.password());
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(UserNotFoundException::new);
    }

    private AuthResponse tokenResponse(User user) {
        return new AuthResponse(jwtIssuer.issue(user), jwtIssuer.expiresInSeconds(), UserResponse.from(user));
    }

    /** Emails are stored lowercased so the unique index actually prevents duplicates. */
    private static String normalise(String email) {
        return email.trim().toLowerCase();
    }
}
