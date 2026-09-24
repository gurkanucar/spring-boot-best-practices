package com.gucardev.eronetomany.book;

import com.gucardev.eronetomany.author.Author;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** The "many" side and owner of the relationship: the {@code book.author_id} column is the foreign key. */
@Getter
@Setter
@Entity
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String isbn;

    // LAZY: ManyToOne defaults to EAGER, which silently joins the author on every book query.
    // Prefer Author.addBook/removeBook over calling setAuthor directly, so both sides stay in sync.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;
}
