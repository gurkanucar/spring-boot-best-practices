package com.gucardev.eronetomany.book;

import com.gucardev.eronetomany.book.dto.BookResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    // Paginates in the database. Going through author.getBooks() would load the whole collection.
    Page<Book> findByAuthorId(Long authorId, Pageable pageable);

    Optional<Book> findByIdAndAuthorId(Long id, Long authorId);

    // Keyset pagination: "where id > :afterId order by id" uses the primary key index and costs
    // the same on page 1 and page 100,000, unlike OFFSET which scans and discards all skipped rows.
    // b.author.id reads the foreign key column directly, no join to author.
    @Query("""
            select new com.gucardev.eronetomany.book.dto.BookResponse(b.id, b.title, b.isbn, b.author.id)
            from Book b
            where b.author.id = :authorId and b.id > :afterId
            order by b.id
            """)
    List<BookResponse> findNextPage(@Param("authorId") Long authorId, @Param("afterId") Long afterId,
                                    Pageable pageable);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);
}
