package ph.pesowise.auth.web;

/** Maps to 400: the request is well-formed JSON but the content is invalid. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
