package com.gucardev.entityauditing.product;

import com.gucardev.entityauditing.common.audit.AuditRevision;
import com.gucardev.entityauditing.common.error.ResourceNotFoundException;
import com.gucardev.entityauditing.product.dto.DeletedProduct;
import com.gucardev.entityauditing.product.dto.PriceChange;
import com.gucardev.entityauditing.product.dto.ProductRevisionResponse;
import com.gucardev.entityauditing.product.dto.ProductSnapshot;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.exception.RevisionDoesNotExistException;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.data.history.Revision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the history, two ways:
 * <ul>
 *   <li>Spring Data's {@link org.springframework.data.repository.history.RevisionRepository}
 *       for the simple cases (one revision of one entity);</li>
 *   <li>Envers' {@link AuditReader} query API for everything else (changed fields, "only
 *       revisions where the price changed", state at a point in time).</li>
 * </ul>
 * Read-only transactions are required: a historic entity's relations (the category) are loaded
 * lazily from the audit tables.
 */
@Service
@Transactional(readOnly = true)
public class ProductHistoryService {

    private final EntityManager entityManager;
    private final ProductRepository productRepository;

    public ProductHistoryService(EntityManager entityManager, ProductRepository productRepository) {
        this.entityManager = entityManager;
        this.productRepository = productRepository;
    }

    /** Every revision of the product, oldest first, including its deletion. */
    public List<ProductRevisionResponse> history(Long id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader().createQuery()
                // true = include the DELETE revision. Each row: [entity, revision entity, RevisionType, changed property names]
                .forRevisionsOfEntityWithChanges(Product.class, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("No history for product " + id);
        }
        return rows.stream().map(row -> {
            AuditRevision revision = (AuditRevision) row[1];
            @SuppressWarnings("unchecked")
            Set<String> changed = new TreeSet<>((Set<String>) row[3]);
            return new ProductRevisionResponse(revision.getId(), label((RevisionType) row[2]), revision.getInstant(),
                    revision.getUsername(), changed, snapshot((Product) row[0]));
        }).toList();
    }

    /** One revision through Spring Data Envers. */
    public ProductRevisionResponse revision(Long id, Long revisionNumber) {
        Revision<Long, Product> revision = productRepository.findRevision(id, revisionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " has no revision " + revisionNumber));
        AuditRevision info = revision.getMetadata().getDelegate();
        String type = switch (revision.getMetadata().getRevisionType()) {
            case INSERT -> "CREATED";
            case UPDATE -> "UPDATED";
            case DELETE -> "DELETED";
            case UNKNOWN -> "UNKNOWN";
        };
        return new ProductRevisionResponse(info.getId(), type,
                info.getInstant(), info.getUsername(), Set.of(), snapshot(revision.getEntity()));
    }

    /** Only the revisions in which the price changed, thanks to the {@code price_mod} flag column. */
    public List<PriceChange> priceHistory(Long id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader().createQuery()
                .forRevisionsOfEntity(Product.class, false, true)
                .add(AuditEntity.id().eq(id))
                .add(AuditEntity.property("price").hasChanged())
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(row -> {
            AuditRevision revision = (AuditRevision) row[1];
            return new PriceChange(revision.getId(), revision.getInstant(), revision.getUsername(), ((Product) row[0]).getPrice());
        }).toList();
    }

    /**
     * Every deleted product, newest deletion first. The last state is only available because of
     * {@code store_data_at_delete: true}; without it the DELETE row holds nothing but the id.
     */
    public List<DeletedProduct> deleted() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader().createQuery()
                .forRevisionsOfEntity(Product.class, false, true)
                .add(AuditEntity.revisionType().eq(RevisionType.DEL))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();
        return rows.stream().map(row -> {
            Product product = (Product) row[0];
            AuditRevision revision = (AuditRevision) row[1];
            return new DeletedProduct(product.getId(), revision.getId(), revision.getInstant(),
                    revision.getUsername(), snapshot(product));
        }).toList();
    }

    /** "What did this product look like at 10:15 yesterday?" */
    public ProductSnapshot asOf(Long id, Instant at) {
        Number revision;
        try {
            revision = reader().getRevisionNumberForDate(at);
        } catch (RevisionDoesNotExistException e) {
            throw new ResourceNotFoundException("Nothing was recorded before " + at);
        }
        Product product = reader().find(Product.class, id, revision);
        if (product == null) {
            throw new ResourceNotFoundException("Product " + id + " did not exist at " + at);
        }
        return snapshot(product);
    }

    private AuditReader reader() {
        return AuditReaderFactory.get(entityManager);
    }

    static ProductSnapshot snapshot(Product p) {
        return new ProductSnapshot(p.getId(), p.getName(), p.getPrice(), p.getStock(),
                p.getCategory().getId(), p.getCategory().getName());
    }

    private static String label(RevisionType type) {
        return switch (type) {
            case ADD -> "CREATED";
            case MOD -> "UPDATED";
            case DEL -> "DELETED";
        };
    }
}
