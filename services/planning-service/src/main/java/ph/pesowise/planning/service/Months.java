package ph.pesowise.planning.service;

import ph.pesowise.planning.web.BadRequestException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public final class Months {

    private Months() {
    }

    /** @param month a YYYY-MM key, or null/blank for the current month */
    public static YearMonth parse(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("'%s' is not a valid month. Use YYYY-MM.".formatted(month));
        }
    }
}
