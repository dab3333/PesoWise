package ph.pesowise.ledger.web;

/**
 * Maps to 404. Deliberately also used when a record exists but belongs to another user —
 * a 403 would confirm the id is real, which is information the caller should not get.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what) {
        super(what + " not found.");
    }
}
