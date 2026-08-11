package ph.pesowise.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ph.pesowise.ledger.domain.Enums.Bucket;
import ph.pesowise.ledger.domain.Enums.Kind;

import java.time.Instant;
import java.util.UUID;

/**
 * A spending or income category. The {@code bucket} column is what makes the 70-20-10 report
 * computable, and a database CHECK guarantees only expense categories carry one.
 *
 * <p>{@code color} is assigned from the chart ramp at seed time and stored, so a category is
 * the same colour on every chart on every page.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Bucket bucket;

    @Column(nullable = false, length = 7)
    private String color;

    /** Seeded categories can be renamed and recoloured but never deleted. */
    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
        // for JPA
    }

    public static Category create(
            UUID userId, String name, Kind kind, Bucket bucket, String color, boolean system) {
        Category category = new Category();
        category.id = UUID.randomUUID();
        category.userId = userId;
        category.name = name;
        category.kind = kind;
        // Mirrors the DB CHECK: income never carries a bucket, expense always does.
        category.bucket = kind == Kind.EXPENSE ? (bucket == null ? Bucket.NEEDS : bucket) : null;
        category.color = color;
        category.system = system;
        category.archived = false;
        category.createdAt = Instant.now();
        return category;
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

    public Kind getKind() {
        return kind;
    }

    public Bucket getBucket() {
        return bucket;
    }

    /** Ignored for income categories, where a bucket would violate the DB CHECK. */
    public void setBucket(Bucket bucket) {
        if (kind == Kind.EXPENSE && bucket != null) this.bucket = bucket;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isSystem() {
        return system;
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
