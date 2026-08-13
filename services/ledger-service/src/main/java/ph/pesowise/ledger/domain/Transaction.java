package ph.pesowise.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ph.pesowise.ledger.domain.Enums.Kind;
import ph.pesowise.ledger.domain.Enums.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One movement of money.
 *
 * <p>{@code amount} is always positive and {@code kind} carries the direction, so SUM()
 * aggregates never need a CASE to work out signs. {@code kind} is copied from the category on
 * write rather than accepted from the client — the category already decides whether something
 * is income or an expense, and letting the two disagree would corrupt every report.
 *
 * <p>Associations are stored as raw ids rather than {@code @ManyToOne}. Nothing here needs to
 * navigate to the account or category entity, and ids keep the aggregate queries plain SQL with
 * no lazy-loading surprises.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, updatable = false)
    private SourceType sourceType;

    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
        // for JPA
    }

    public static Transaction create(
            UUID userId,
            UUID accountId,
            Category category,
            BigDecimal amount,
            LocalDate txnDate,
            String note,
            SourceType sourceType,
            UUID sourceId) {
        Transaction transaction = new Transaction();
        transaction.id = UUID.randomUUID();
        transaction.userId = userId;
        transaction.accountId = accountId;
        transaction.categoryId = category.getId();
        transaction.kind = category.getKind();
        transaction.amount = amount;
        transaction.txnDate = txnDate;
        transaction.note = note;
        transaction.sourceType = sourceType;
        transaction.sourceId = sourceId;
        transaction.createdAt = Instant.now();
        return transaction;
    }

    /**
     * Reconstructs a transaction from an export file with its original id and timestamp.
     * {@code kind} is taken directly from the export rather than re-derived from a {@link
     * Category}, since the category row is being restored in the same import pass rather than
     * already existing to look up.
     */
    public static Transaction restore(
            UUID id, UUID userId, UUID accountId, UUID categoryId, Kind kind, BigDecimal amount,
            LocalDate txnDate, String note, SourceType sourceType, UUID sourceId, Instant createdAt) {
        Transaction transaction = new Transaction();
        transaction.id = id;
        transaction.userId = userId;
        transaction.accountId = accountId;
        transaction.categoryId = categoryId;
        transaction.kind = kind;
        transaction.amount = amount;
        transaction.txnDate = txnDate;
        transaction.note = note;
        transaction.sourceType = sourceType;
        transaction.sourceId = sourceId;
        transaction.createdAt = createdAt;
        return transaction;
    }

    /** Re-derives {@code kind} whenever the category changes, keeping the two consistent. */
    public void moveTo(Category category) {
        this.categoryId = category.getId();
        this.kind = category.getKind();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public Kind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
