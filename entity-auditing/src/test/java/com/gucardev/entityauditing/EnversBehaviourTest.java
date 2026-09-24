package com.gucardev.entityauditing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.entityauditing.category.Category;
import com.gucardev.entityauditing.category.CategoryRepository;
import com.gucardev.entityauditing.product.Product;
import com.gucardev.entityauditing.product.ProductHistoryService;
import com.gucardev.entityauditing.product.ProductRepository;
import com.gucardev.entityauditing.product.dto.ProductRevisionResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Envers rules that are easy to get wrong, shown without HTTP. */
@SpringBootTest
class EnversBehaviourTest {

    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductHistoryService historyService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbc;

    @Test
    void oneTransactionIsOneRevisionEvenForSeveralChangesAndEntities() {
        Long id = tx.execute(status -> {
            Category category = categoryRepository.save(new Category("Bundle"));
            Product product = productRepository.saveAndFlush(new Product("Set", new BigDecimal("10"), 1, category));
            product.update("Set", new BigDecimal("12"), 2, category); // second change, same transaction
            return product.getId();
        });

        List<ProductRevisionResponse> history = historyService.history(id);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().type()).isEqualTo("CREATED");
        assertThat(history.getFirst().state().price()).isEqualByComparingTo("12"); // final state of the transaction
        assertThat(history.getFirst().changedBy()).isEqualTo("system");             // no HTTP request

        List<String> changedEntities = jdbc.sql("select entity_name from revision_changed_entity where rev = :rev order by 1")
                .param("rev", history.getFirst().revision()).query(String.class).list();
        assertThat(changedEntities).containsExactly(
                "com.gucardev.entityauditing.category.Category", "com.gucardev.entityauditing.product.Product");
    }

    @Test
    void bulkUpdatesBypassEnvers() {
        Long id = tx.execute(status -> {
            Category category = categoryRepository.save(new Category("Bulk"));
            return productRepository.save(new Product("Cheap", new BigDecimal("1"), 1, category)).getId();
        });

        // JPQL/SQL bulk statements do not go through entity events: no audit row is written.
        tx.executeWithoutResult(status -> entityManager
                .createQuery("update Product p set p.price = :price where p.id = :id")
                .setParameter("price", new BigDecimal("999")).setParameter("id", id)
                .executeUpdate());

        assertThat(productRepository.findById(id).orElseThrow().getPrice()).isEqualByComparingTo("999");
        assertThat(historyService.history(id)).hasSize(1);
        assertThat(historyService.history(id).getFirst().state().price()).isEqualByComparingTo("1");
    }

    @Test
    void auditTablesAndColumns() {
        List<String> tables = jdbc.sql("""
                        select lower(table_name) from information_schema.tables
                        where table_schema = 'PUBLIC' order by 1""")
                .query(String.class).list();
        assertThat(tables).containsExactly("category", "category_history", "product", "product_history",
                "revision_changed_entity", "revision_info");

        List<String> columns = columns("PRODUCT_HISTORY");
        assertThat(columns).contains("id", "rev", "revtype", "revend",
                "name", "name_mod", "price", "price_mod", "stock", "stock_mod", "category_id", "category_mod");
        // @NotAudited field and the Spring Data auditing columns are not copied
        assertThat(columns).doesNotContain("last_viewed_at", "created_by", "last_modified_at");
    }

    private List<String> columns(String table) {
        return jdbc.sql("""
                        select lower(column_name) from information_schema.columns
                        where table_name = :table order by ordinal_position""")
                .param("table", table).query(String.class).list();
    }
}
