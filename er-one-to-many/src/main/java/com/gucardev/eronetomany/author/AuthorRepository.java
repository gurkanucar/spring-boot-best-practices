package com.gucardev.eronetomany.author;

import com.gucardev.eronetomany.author.dto.AuthorSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // Single-author reads fetch the collection with a join. Not applied to the paged findAll:
    // a fetch join on a collection cannot be paginated in SQL, so Hibernate would load every
    // row and page in memory. The default list endpoint relies on @BatchSize instead.
    @Override
    @EntityGraph(attributePaths = "books")
    Optional<Author> findById(Long id);

    // Strategy "ids first, then fetch join", step 1: paginate cheap ids in the database.
    @Query(value = "select a.id from Author a", countQuery = "select count(a) from Author a")
    Page<Long> findPageOfIds(Pageable pageable);

    // Step 2: load exactly those authors with their books in a single join. Safe because the
    // page was already cut in step 1; there is no LIMIT here to slice through a collection.
    // (Hibernate 6+ deduplicates the root entity itself, so no "select distinct" is needed.)
    @Query("select a from Author a left join fetch a.books where a.id in :ids")
    List<Author> findAllWithBooksByIdIn(@Param("ids") Collection<Long> ids);

    // Strategy "DTO projection": no entities, no collections, one aggregate query.
    @Query(value = """
            select new com.gucardev.eronetomany.author.dto.AuthorSummary(a.id, a.name, count(b))
            from Author a left join a.books b
            group by a.id, a.name
            """,
            countQuery = "select count(a) from Author a")
    Page<AuthorSummary> findSummaries(Pageable pageable);
}
