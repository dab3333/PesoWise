package ph.pesowise.auth.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.pesowise.auth.admin.InternalAdminDtos.SignupDay;
import ph.pesowise.auth.admin.InternalAdminDtos.UpdateUserRequest;
import ph.pesowise.auth.admin.InternalAdminDtos.UserPage;
import ph.pesowise.auth.admin.InternalAdminDtos.UserStats;
import ph.pesowise.auth.admin.InternalAdminDtos.UserSummary;
import ph.pesowise.auth.user.User;
import ph.pesowise.auth.user.UserRepository;
import ph.pesowise.auth.web.BadRequestException;
import ph.pesowise.auth.web.UserNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The cross-user queries this service was never asked for until an admin panel needed them.
 * Every method here sees every account — deliberately: the endpoints these back are exposed only
 * under {@code /internal/admin/**}, which the gateway does not route, so the only caller that can
 * ever reach them is admin-service, over the Compose network.
 */
@Service
public class InternalAdminService {

    private static final Logger log = LoggerFactory.getLogger(InternalAdminService.class);

    private final UserRepository users;

    public InternalAdminService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserPage listUsers(String q, int page, int size) {
        var result = users.search(blankToNull(q), PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return new UserPage(
                result.getContent().stream().map(UserSummary::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public UserSummary updateUser(UUID id, UpdateUserRequest request) {
        User user = users.findById(id).orElseThrow(UserNotFoundException::new);

        if (request.role() != null) {
            User.Role role = parseRole(request.role());
            user.setRole(role);
        }
        if (request.disabled() != null) {
            user.setDisabled(request.disabled());
        }

        log.info("Admin update on {}: role={}, disabled={}", user.getEmail(), request.role(), request.disabled());
        return UserSummary.from(user);
    }

    @Transactional(readOnly = true)
    public UserStats stats() {
        long total = users.count();
        long verified = users.countByEmailVerifiedTrue();
        long disabled = users.countByDisabledTrue();
        long admins = users.countByRole(User.Role.ADMIN);

        Map<LocalDate, Long> byDay = users.countSignupsPerDay().stream()
                .collect(Collectors.toMap(SignupDayRow::getDay, SignupDayRow::getCount));

        // Zero-filled: a dashboard chart with gaps for quiet days reads as missing data, not as
        // "nothing happened" — the same reasoning ledger-service's daily report already applies.
        LocalDate today = LocalDate.now();
        List<SignupDay> series = Stream.iterate(today.minusDays(29), d -> d.plusDays(1))
                .limit(30)
                .map(day -> new SignupDay(day, byDay.getOrDefault(day, 0L)))
                .toList();

        return new UserStats(total, verified, disabled, admins, series);
    }

    private static User.Role parseRole(String raw) {
        try {
            return User.Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role must be USER or ADMIN.");
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
