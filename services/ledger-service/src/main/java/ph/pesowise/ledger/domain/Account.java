package ph.pesowise.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ph.pesowise.ledger.domain.Enums.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A wallet: cash, a bank account, GCash, a credit card.
 *
 * <p>The current balance is deliberately not a column — it is derived as
 * {@code opening_balance + income - expense}. A stored balance is one more thing to keep in
 * step with the transaction rows, and it would drift the first time a transaction is edited.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public static Account create(UUID userId, String name, AccountType type, BigDecimal openingBalance) {
        Account account = new Account();
        account.id = UUID.randomUUID();
        account.userId = userId;
        account.name = name;
        account.type = type;
        account.openingBalance = openingBalance;
        account.archived = false;
        account.createdAt = Instant.now();
        return account;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public boolean isArchived() {
        return archived;
    }

    public void archive() {
        this.archived = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
