package com.gucardev.ermanytomany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.ermanytomany.simple.category.CategoryRepository;
import com.gucardev.ermanytomany.simple.product.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class SimpleManyToManyApiTest {

    private static final String CATEGORIES = "/api/simple/categories";
    private static final String PRODUCTS = "/api/simple/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clean() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private long createCategory(String name) throws Exception {
        return idOf(mockMvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn());
    }

    private static String productJson(String name, String price, Long... categoryIds) {
        String ids = java.util.Arrays.stream(categoryIds).map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"name\": \"%s\", \"price\": %s, \"categoryIds\": [%s]}".formatted(name, price, ids);
    }

    private long createProduct(String name, String price, Long... categoryIds) throws Exception {
        return idOf(mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, price, categoryIds)))
                .andExpect(status().isCreated()).andReturn());
    }

    private long joinRows() {
        return transactionTemplate.execute(s -> ((Number) entityManager
                .createNativeQuery("select count(*) from product_category").getSingleResult()).longValue());
    }

    @Test
    void categoryCrudAndUniqueName() throws Exception {
        long id = createCategory("Books");

        mockMvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Books\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(CATEGORIES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Novels\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Novels"));
        mockMvc.perform(get(CATEGORIES + "/" + id)).andExpect(jsonPath("$.name").value("Novels"));
        mockMvc.perform(get(CATEGORIES)).andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CATEGORIES + "/999")).andExpect(status().isNotFound());
    }

    @Test
    void createsProductWithCategories() throws Exception {
        long books = createCategory("Books");
        long sale = createCategory("Sale");

        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Novel", "9.99", books, sale)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(9.99))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].name").value("Books"));
        assertThat(joinRows()).isEqualTo(2);
    }

    @Test
    void createsProductWithoutCategories() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pen\", \"price\": 1.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    void rejectsInvalidPayloadAndUnknownCategories() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"price\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Novel", "9.99", 404L)))
                .andExpect(status().isNotFound());
        assertThat(productRepository.count()).isZero();
    }

    @Test
    void putReplacesTheWholeCategorySet() throws Exception {
        long books = createCategory("Books");
        long sale = createCategory("Sale");
        long fresh = createCategory("New");
        long product = createProduct("Novel", "9.99", books, sale);

        mockMvc.perform(put(PRODUCTS + "/" + product).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Novel v2", "12.50", sale, fresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novel v2"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].name").value("New"))
                .andExpect(jsonPath("$.categories[1].name").value("Sale"));
        assertThat(joinRows()).isEqualTo(2);
        assertThat(categoryRepository.count()).isEqualTo(3);
    }

    @Test
    void addsAndRemovesASingleCategory() throws Exception {
        long books = createCategory("Books");
        long product = createProduct("Novel", "9.99");

        mockMvc.perform(put(PRODUCTS + "/" + product + "/categories/" + books))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories.length()").value(1));
        // idempotent
        mockMvc.perform(put(PRODUCTS + "/" + product + "/categories/" + books))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories.length()").value(1));

        mockMvc.perform(delete(PRODUCTS + "/" + product + "/categories/" + books))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(PRODUCTS + "/" + product + "/categories/" + books))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(PRODUCTS + "/" + product + "/categories/999")).andExpect(status().isNotFound());
        assertThat(joinRows()).isZero();
    }

    @Test
    void filtersProductsByCategoryAndPaginates() throws Exception {
        long books = createCategory("Books");
        long toys = createCategory("Toys");
        createProduct("Novel", "9.99", books);
        createProduct("Atlas", "19.99", books, toys);
        createProduct("Robot", "29.99", toys);

        mockMvc.perform(get(PRODUCTS + "?categoryId=" + books + "&sort=name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Atlas"))
                // the filter narrows the products, not the categories shown for each of them
                .andExpect(jsonPath("$.content[0].categories.length()").value(2));
        mockMvc.perform(get(PRODUCTS + "?size=2&sort=name"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void deletingProductRemovesJoinRowsButKeepsCategories() throws Exception {
        long books = createCategory("Books");
        long product = createProduct("Novel", "9.99", books);

        mockMvc.perform(delete(PRODUCTS + "/" + product)).andExpect(status().isNoContent());

        assertThat(joinRows()).isZero();
        assertThat(categoryRepository.count()).isEqualTo(1);
    }

    @Test
    void deletingCategoryDetachesProductsButKeepsThem() throws Exception {
        long books = createCategory("Books");
        long sale = createCategory("Sale");
        long product = createProduct("Novel", "9.99", books, sale);

        mockMvc.perform(delete(CATEGORIES + "/" + books)).andExpect(status().isNoContent());

        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(joinRows()).isEqualTo(1);
        mockMvc.perform(get(PRODUCTS + "/" + product))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].name").value("Sale"));
    }
}
