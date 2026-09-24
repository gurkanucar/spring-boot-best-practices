package com.gucardev.eronetomany;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.eronetomany.author.Author;
import com.gucardev.eronetomany.author.AuthorRepository;
import com.gucardev.eronetomany.author.AuthorService;
import com.gucardev.eronetomany.author.dto.AuthorResponse;
import com.gucardev.eronetomany.author.dto.AuthorSummary;
import com.gucardev.eronetomany.book.Book;
import com.gucardev.eronetomany.book.BookService;
import com.gucardev.eronetomany.book.dto.BookResponse;
import com.gucardev.eronetomany.common.KeysetPage;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Proves the query strategies by counting the SQL statements Hibernate actually executes. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryPerformanceTest {

    private static final int AUTHORS = 30;
    private static final int BOOKS_PER_AUTHOR = 2;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private BookService bookService;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics stats;

    @BeforeEach
    void seed() {
        authorRepository.deleteAll();
        List<Author> authors = new ArrayList<>();
        for (int a = 0; a < AUTHORS; a++) {
            Author author = new Author();
            author.setName("Author %02d".formatted(a));
            for (int b = 0; b < BOOKS_PER_AUTHOR; b++) {
                Book book = new Book();
                book.setTitle("Book %d-%d".formatted(a, b));
                book.setIsbn("ISBN-%d-%d".formatted(a, b));
                author.addBook(book);
            }
            authors.add(author);
        }
        authorRepository.saveAll(authors);

        stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
    }

    @Test
    void defaultListUsesBatchFetchInsteadOfNPlusOne() {
        Page<AuthorResponse> page = authorService.list(PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getContent()).allSatisfy(a -> assertThat(a.books()).hasSize(BOOKS_PER_AUTHOR));
        // authors page + count + ONE batched books query (naive lazy loading would be 1 + 1 + 20)
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void idsFirstFetchJoinIsPagedInSqlAndKeepsOrder() {
        Page<AuthorResponse> page = authorService.listWithFetchJoin(
                PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "name")));

        assertThat(page.getTotalElements()).isEqualTo(AUTHORS);
        assertThat(page.getContent()).extracting(AuthorResponse::name)
                .first().isEqualTo("Author 19");
        assertThat(page.getContent()).extracting(AuthorResponse::name)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(page.getContent()).allSatisfy(a -> assertThat(a.books()).hasSize(BOOKS_PER_AUTHOR));
        // ids page + count + one fetch-join query
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void dtoProjectionNeedsNoEntitiesAndNoBookQuery() {
        Page<AuthorSummary> page = authorService.listSummaries(PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getContent()).allSatisfy(s -> assertThat(s.bookCount()).isEqualTo(BOOKS_PER_AUTHOR));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
        assertThat(stats.getEntityLoadCount()).isZero();
    }

    @Test
    void keysetPaginationWalksAllBooksWithoutCountQueries() {
        Long authorId = authorRepository.findAll().getFirst().getId();
        // give the first author 5 books in total
        for (int i = 0; i < 3; i++) {
            bookService.add(authorId, new com.gucardev.eronetomany.book.dto.BookRequest("Extra " + i, "X-" + i));
        }
        stats.clear();

        List<String> titles = new ArrayList<>();
        long cursor = 0;
        int pages = 0;
        while (true) {
            KeysetPage<BookResponse> page = bookService.listAfter(authorId, cursor, 2);
            page.items().forEach(b -> titles.add(b.title()));
            pages++;
            if (page.nextCursor() == null) {
                break;
            }
            cursor = page.nextCursor();
        }

        assertThat(titles).hasSize(5).doesNotHaveDuplicates();
        assertThat(pages).isEqualTo(3);
        // per page: author existence check + one keyset query; never a count(*)
        assertThat(stats.getPrepareStatementCount()).isEqualTo(pages * 2L);
    }
}
