package com.gucardev.entityauditing.product;

import com.gucardev.entityauditing.category.Category;
import com.gucardev.entityauditing.common.audit.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * {@code @Audited}: every insert, update and delete in a transaction writes a copy of the row to
 * {@code product_history}, linked to one {@code revision_info} row.
 *
 * <p>{@code withModifiedFlag = true} adds a boolean {@code <field>_mod} column per property, so
 * we can ask "in which revisions did the price change?" and "what changed in revision N?".
 */
@Entity
@Audited(withModifiedFlag = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    /** The audit row stores only the category id; Envers loads the category as of that revision. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Changes constantly and has no business meaning: auditing it would flood the history. */
    @NotAudited
    private Instant lastViewedAt;

    public Product(String name, BigDecimal price, int stock, Category category) {
        update(name, price, stock, category);
    }

    public void update(String name, BigDecimal price, int stock, Category category) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    public void markViewed() {
        this.lastViewedAt = Instant.now();
    }
}
