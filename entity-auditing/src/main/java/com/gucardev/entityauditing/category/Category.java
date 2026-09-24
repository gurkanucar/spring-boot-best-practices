package com.gucardev.entityauditing.category;

import com.gucardev.entityauditing.common.audit.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

/**
 * Audited as well, so that an old product revision shows the category as it was AT THAT TIME
 * (renaming a category later does not rewrite history).
 */
@Entity
@Audited
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    public Category(String name) {
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }
}
