package ph.pesowise.planning.web;

/**
 * Maps to 403: the caller is authenticated but lacks the authority for this action.
 *
 * <p>Distinct from the 404 that a record belonging to another user returns. That case hides
 * whether the id exists at all; this one is about an operation that is not user-scoped in the
 * first place, where there is nothing to conceal.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
