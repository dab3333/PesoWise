package ph.pesowise.admin.web;

/** Maps to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
