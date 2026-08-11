package ph.pesowise.ledger.web;

/** Maps to 400 for rules bean validation cannot express, such as a from-date after a to-date. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
