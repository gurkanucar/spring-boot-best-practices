package com.gucardev.ermanytomany.simple.category;

import com.gucardev.ermanytomany.simple.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Inverse side of the many-to-many: owns no table, just mirrors {@code Product.categories}. */
@Getter
@Setter
@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // mappedBy: the join table is owned (and written) by Product.categories.
    // Product uses default identity equals/hashCode, which is safe for managed instances
    // inside one transaction; an id-based equals breaks before persist (id is still null).
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ManyToMany(mappedBy = "categories")
    private Set<Product> products = new HashSet<>();

    /** Read-only view: change membership through Product.addCategory/removeCategory. */
    public Set<Product> getProducts() {
        return Collections.unmodifiableSet(products);
    }

    /** Inverse-side hook called by Product.addCategory; do not call directly. */
    public void linkProduct(Product product) {
        products.add(product);
    }

    /** Inverse-side hook called by Product.removeCategory; do not call directly. */
    public void unlinkProduct(Product product) {
        products.remove(product);
    }
}
