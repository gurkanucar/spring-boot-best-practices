package com.gucardev.eronetomany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.eronetomany.author.AuthorRepository;
import com.gucardev.eronetomany.book.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorBookApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void clean() {
        authorRepository.deleteAll();
    }

    private static String book(String title, String isbn) {
        return "{\"title\": \"%s\", \"isbn\": \"%s\"}".formatted(title, isbn);
    }

    private static String author(String name, String... books) {
        return "{\"name\": \"%s\", \"books\": [%s]}".formatted(name, String.join(",", books));
    }

    private long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private long createAuthor(String name, String... books) throws Exception {
        return idOf(mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON)
                        .content(author(name, books)))
                .andExpect(status().isCreated()).andReturn());
    }

    private long addBook(long authorId, String title, String isbn) throws Exception {
        return idOf(mockMvc.perform(post("/api/authors/" + authorId + "/books")
                        .contentType(MediaType.APPLICATION_JSON).content(book(title, isbn)))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void createsAuthorTogetherWithBooks() throws Exception {
        mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON)
                        .content(author("Orwell", book("1984", "ISBN-1"), book("Animal Farm", "ISBN-2"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Orwell"))
                .andExpect(jsonPath("$.books.length()").value(2))
                .andExpect(jsonPath("$.books[0].title").value("1984"))
                .andExpect(jsonPath("$.books[0].authorId").isNumber());
    }

    @Test
    void createsAuthorWithoutBooks() throws Exception {
        mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Nobody\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.books.length()").value(0));
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON)
                        .content(author("", book("", "ISBN-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void rejectsDuplicateIsbnAcrossAndWithinRequests() throws Exception {
        createAuthor("Orwell", book("1984", "ISBN-1"));

        mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON)
                        .content(author("Eve", book("Copy", "ISBN-1"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/authors").contentType(MediaType.APPLICATION_JSON)
                        .content(author("Eve", book("A", "ISBN-9"), book("B", "ISBN-9"))))
                .andExpect(status().isConflict());
        assertThat(authorRepository.count()).isEqualTo(1);
    }

    @Test
    void listsAuthorsWithTheirBooks() throws Exception {
        createAuthor("Orwell", book("1984", "ISBN-1"), book("Animal Farm", "ISBN-2"));
        createAuthor("Huxley");

        mockMvc.perform(get("/api/authors?sort=name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Huxley"))
                .andExpect(jsonPath("$.content[0].books.length()").value(0))
                .andExpect(jsonPath("$.content[1].books.length()").value(2));
    }

    @Test
    void updatesAuthorNameWithoutTouchingBooks() throws Exception {
        long id = createAuthor("Orwel", book("1984", "ISBN-1"));

        mockMvc.perform(put("/api/authors/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Orwell\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Orwell"))
                .andExpect(jsonPath("$.books.length()").value(1));
    }

    @Test
    void returns404ForUnknownAuthor() throws Exception {
        mockMvc.perform(get("/api/authors/999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/authors/999/books")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/authors/999/books").contentType(MediaType.APPLICATION_JSON)
                        .content(book("X", "ISBN-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAuthorCascadesToBooks() throws Exception {
        long id = createAuthor("Orwell", book("1984", "ISBN-1"), book("Animal Farm", "ISBN-2"));

        mockMvc.perform(delete("/api/authors/" + id)).andExpect(status().isNoContent());

        assertThat(bookRepository.count()).isZero();
    }

    @Test
    void addsAndPagesBooksOfAnAuthor() throws Exception {
        long id = createAuthor("Orwell");
        addBook(id, "1984", "ISBN-1");
        addBook(id, "Animal Farm", "ISBN-2");
        addBook(id, "Homage", "ISBN-3");

        mockMvc.perform(get("/api/authors/" + id + "/books?size=2&sort=title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("1984"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void addBookRejectsDuplicateIsbn() throws Exception {
        long id = createAuthor("Orwell", book("1984", "ISBN-1"));

        mockMvc.perform(post("/api/authors/" + id + "/books").contentType(MediaType.APPLICATION_JSON)
                        .content(book("Other", "ISBN-1")))
                .andExpect(status().isConflict());
    }

    @Test
    void updatesBookAndAllowsKeepingItsOwnIsbn() throws Exception {
        long id = createAuthor("Orwell");
        long bookId = addBook(id, "1984", "ISBN-1");
        addBook(id, "Animal Farm", "ISBN-2");

        mockMvc.perform(put("/api/authors/" + id + "/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON).content(book("Nineteen Eighty-Four", "ISBN-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nineteen Eighty-Four"));

        mockMvc.perform(put("/api/authors/" + id + "/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON).content(book("1984", "ISBN-2")))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotTouchABookThroughTheWrongAuthor() throws Exception {
        long orwell = createAuthor("Orwell");
        long huxley = createAuthor("Huxley");
        long bookId = addBook(orwell, "1984", "ISBN-1");

        mockMvc.perform(put("/api/authors/" + huxley + "/books/" + bookId)
                        .contentType(MediaType.APPLICATION_JSON).content(book("Hijacked", "ISBN-1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/authors/" + huxley + "/books/" + bookId))
                .andExpect(status().isNotFound());
        assertThat(bookRepository.count()).isEqualTo(1);
    }

    @Test
    void removingBookKeepsAuthorAndDeletesRow() throws Exception {
        long id = createAuthor("Orwell");
        long bookId = addBook(id, "1984", "ISBN-1");
        addBook(id, "Animal Farm", "ISBN-2");

        mockMvc.perform(delete("/api/authors/" + id + "/books/" + bookId)).andExpect(status().isNoContent());

        assertThat(bookRepository.count()).isEqualTo(1);
        assertThat(authorRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/books/" + bookId)).andExpect(status().isNotFound());
    }

    @Test
    void looksUpAuthorFromBook() throws Exception {
        long id = createAuthor("Orwell");
        long bookId = addBook(id, "1984", "ISBN-1");

        mockMvc.perform(get("/api/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(id));
    }
}
