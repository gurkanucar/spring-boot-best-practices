package com.gucardev.eronetomany.author;

import com.gucardev.eronetomany.book.Book;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** The "one" side and inverse side of the relationship: owns no column, mirrors {@code Book.author}. */
@Getter
@Setter
@Entity
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // mappedBy: the owning side is the "author" field on Book (it holds the foreign key).
    // cascade ALL: saving/deleting an Author saves/deletes its books.
    // orphanRemoval: removing a book from this collection deletes its row.
    // BatchSize: when many authors are loaded, their collections are fetched in batches of up to
    //            50 (one "where author_id in (...)" query) instead of one query per author (N+1).
    // Book uses default identity equals/hashCode, which is safe here because we only compare
    // managed instances inside one transaction; an id-based equals would break before persist.
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Book> books = new HashSet<>();

    /** Read-only view: callers must go through addBook/removeBook so both sides stay in sync. */
    public Set<Book> getBooks() {
        return Collections.unmodifiableSet(books);
    }

    public void addBook(Book book) {
        books.add(book);
        book.setAuthor(this);
    }

    public void removeBook(Book book) {
        books.remove(book);
        book.setAuthor(null);
    }
}
