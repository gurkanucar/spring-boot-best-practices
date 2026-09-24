package com.gucardev.entityauditing.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * "Who created it, who changed it last, and when" as columns on the entity's own table
 * (Spring Data JPA auditing). Cheap and always at hand, but it only knows the LAST change;
 * the full history comes from Envers.
 *
 * <p>These fields are not copied into the {@code *_history} audit tables: Envers ignores
 * properties of a {@code @MappedSuperclass} that is not itself {@code @Audited}. That is intended
 * here, because each revision already records who and when.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseAuditedEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @LastModifiedBy
    @Column(name = "last_modified_by", nullable = false, length = 50)
    private String lastModifiedBy;
}
