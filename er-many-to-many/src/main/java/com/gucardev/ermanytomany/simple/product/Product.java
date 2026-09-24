package com.gucardev.ermanytomany.simple.product;

import com.gucardev.ermanytomany.simple.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** Owning side of the many-to-many: the {@code product_category} join table is defined and written here. */
@Getter
@Setter
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // No cascade on purpose: categories are independent entities with their own lifecycle.
    // Cascade REMOVE/ALL here would delete a shared category when one product is deleted.
    // Use a Set, not a List: Hibernate then issues targeted inserts/deletes on the join table,
    // whereas a List (bag) deletes ALL of the product's join rows and re-inserts them.
    // BatchSize: load the categories of up to 50 products per query when listing (avoids N+1).
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
            name = "product_category",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    /** Read-only view: callers must go through addCategory/removeCategory so both sides stay in sync. */
    public Set<Category> getCategories() {
        return Collections.unmodifiableSet(categories);
    }

    public void addCategory(Category category) {
        categories.add(category);
        category.linkProduct(this);
    }

    public void removeCategory(Category category) {
        categories.remove(category);
        category.unlinkProduct(this);
    }
}
