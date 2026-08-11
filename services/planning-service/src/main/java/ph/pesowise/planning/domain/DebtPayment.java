package ph.pesowise.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The debt side of a payment event. The cash side is a transaction in the ledger database, and
 * {@link #ledgerTxnId} points at it — money is recorded in exactly one place.
 */
@Entity
@Table(name = "debt_payments")
public class DebtPayment {

    @Id
    private UUID id;

    @Column(name = "debt_id", nullable = false, updatable = false)
    private UUID debtId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(length = 255)
    private String note;

    @Column(name = "ledger_txn_id", updatable = false)
    private UUID ledgerTxnId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DebtPayment() {
        // for JPA
    }

    public static DebtPayment create(
            UUID userId,
            UUID debtId,
            BigDecimal amount,
            LocalDate paidOn,
            String note,
            UUID ledgerTxnId) {
        DebtPayment payment = new DebtPayment();
        payment.id = UUID.randomUUID();
        payment.debtId = debtId;
        payment.userId = userId;
        payment.amount = amount;
        payment.paidOn = paidOn;
        payment.note = note;
        payment.ledgerTxnId = ledgerTxnId;
        payment.createdAt = Instant.now();
        return payment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDebtId() {
        return debtId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getNote() {
        return note;
    }

    public UUID getLedgerTxnId() {
        return ledgerTxnId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
