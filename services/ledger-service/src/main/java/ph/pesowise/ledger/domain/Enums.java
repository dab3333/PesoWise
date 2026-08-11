package ph.pesowise.ledger.domain;

/** Shared vocabulary for the ledger. Values must match the CHECK constraints in V1__init.sql. */
public final class Enums {

    private Enums() {
    }

    public enum AccountType {
        CASH, BANK, EWALLET, CREDIT_CARD
    }

    public enum Kind {
        INCOME, EXPENSE
    }

    /**
     * The 70-20-10 buckets. Only expense categories carry one — the method divides spending,
     * not income.
     */
    public enum Bucket {
        NEEDS, WANTS, SAVINGS
    }

    /** What created a transaction. Anything other than MANUAL came from planning-service. */
    public enum SourceType {
        MANUAL, RECURRING_BILL, DEBT_PAYMENT, GOAL_CONTRIBUTION
    }
}
