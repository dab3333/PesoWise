package ph.pesowise.planning.ledger;

import org.springframework.cloud.openfeign.FeignFormatterRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;

/**
 * Makes Feign render {@code LocalDate} query parameters as ISO-8601.
 *
 * <p>Without this, Feign falls back to default locale formatting and sends {@code 8/1/26} rather
 * than {@code 2026-08-01}, which ledger-service rejects with a 400 — a failure that only shows up
 * at runtime, since the Java signatures on both sides look perfectly correct.
 *
 * <p>Registered globally rather than annotating each parameter with {@code @DateTimeFormat}, so
 * every date parameter added later is right by default instead of needing to be remembered.
 */
@Configuration
public class FeignDateConfig {

    @Bean
    public FeignFormatterRegistrar isoDateFormatterRegistrar() {
        return registry -> {
            DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
            registrar.setUseIsoFormat(true);
            registrar.registerFormatters(registry);
        };
    }
}
