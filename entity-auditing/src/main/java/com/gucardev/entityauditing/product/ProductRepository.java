package com.gucardev.entityauditing.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.history.RevisionRepository;

/**
 * {@link RevisionRepository} (Spring Data Envers) adds read methods over the history:
 * {@code findRevisions(id)}, {@code findRevision(id, rev)}, {@code findLastChangeRevision(id)}.
 * Spring Boot registers the Envers repository factory automatically when spring-data-envers is
 * on the classpath. The type parameters are entity, id and revision number types.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, RevisionRepository<Product, Long, Long> {
}
