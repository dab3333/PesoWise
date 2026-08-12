package ph.pesowise.admin.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.pesowise.admin.clients.AuthAdminClient;
import ph.pesowise.admin.clients.AuthAdminDtos.UserSummary;

/**
 * CSV exports. Only {@code users.csv} exists so far — it proves the pattern (auth-service data,
 * streamed as a download, admin-only) rather than building a generic report framework before a
 * second report has asked for one. A transactions or debts export follows the same shape:
 * {@code Feign call -> rows -> writeCsv}.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    /** One request, not paginated: an export is read once, not scrolled, so there is no page to
     * ask for. Fine at the scale a personal-finance app's user table actually reaches; a system
     * with genuinely large tables would page through and stream rather than build the file in
     * memory. */
    private static final int EXPORT_PAGE_SIZE = 100_000;

    private final AuthAdminClient authClient;

    public AdminReportController(AuthAdminClient authClient) {
        this.authClient = authClient;
    }

    @GetMapping("/users.csv")
    public ResponseEntity<String> usersCsv() {
        var users = authClient.users(null, 0, EXPORT_PAGE_SIZE).items();

        StringBuilder csv = new StringBuilder("id,email,display_name,role,email_verified,disabled,created_at\n");
        for (UserSummary user : users) {
            csv.append(csvRow(
                    user.id(), user.email(), user.displayName(), user.role(),
                    user.emailVerified(), user.disabled(), user.createdAt().toString()));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users.csv\"")
                .body(csv.toString());
    }

    private static String csvRow(Object... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) row.append(',');
            row.append(escape(String.valueOf(values[i])));
        }
        return row.append('\n').toString();
    }

    /** Quotes a field only when it contains something a bare CSV cell cannot hold safely. */
    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
