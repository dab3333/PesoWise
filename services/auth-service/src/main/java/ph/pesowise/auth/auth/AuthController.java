package ph.pesowise.auth.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.auth.api.AuthDtos.AuthResponse;
import ph.pesowise.auth.api.AuthDtos.LoginRequest;
import ph.pesowise.auth.api.AuthDtos.RegisterRequest;
import ph.pesowise.auth.api.AuthDtos.UserResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Injected by the gateway after it verifies the token. Never trust a client's copy. */
    static final String USER_ID_HEADER = "X-User-Id";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@RequestHeader(USER_ID_HEADER) UUID userId) {
        return authService.currentUser(userId);
    }
}
