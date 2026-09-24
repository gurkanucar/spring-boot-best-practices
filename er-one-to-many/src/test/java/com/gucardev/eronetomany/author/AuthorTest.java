package com.gucardev.eronetomany.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.eronetomany.book.Book;
import org.junit.jupiter.api.Test;

class AuthorTest {

    @Test
    void addBookSetsBothSides() {
        Author author = new Author();
        Book book = new Book();

        author.addBook(book);

        assertThat(author.getBooks()).containsExactly(book);
        assertThat(book.getAuthor()).isSameAs(author);
    }

    @Test
    void removeBookClearsBothSides() {
        Author author = new Author();
        Book book = new Book();
        author.addBook(book);

        author.removeBook(book);

        assertThat(author.getBooks()).isEmpty();
        assertThat(book.getAuthor()).isNull();
    }

    @Test
    void booksCollectionIsNotModifiableFromOutside() {
        Author author = new Author();

        assertThatThrownBy(() -> author.getBooks().add(new Book()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
