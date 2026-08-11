package ph.pesowise.planning.web;

/** Maps to 409: a duplicate name, or a delete blocked by existing references. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
