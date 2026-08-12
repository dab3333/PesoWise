package ph.pesowise.auth.admin;

import java.time.LocalDate;

/** Native-query projection backing {@code UserRepository.countSignupsPerDay}. */
public interface SignupDayRow {
    LocalDate getDay();
    long getCount();
}
