package com.gucardev.ermanytomany;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.ermanytomany.complex.course.Course;
import com.gucardev.ermanytomany.complex.course.CourseRepository;
import com.gucardev.ermanytomany.complex.enrollment.EnrollmentService;
import com.gucardev.ermanytomany.complex.enrollment.dto.EnrollmentResponse;
import com.gucardev.ermanytomany.complex.student.Student;
import com.gucardev.ermanytomany.complex.student.StudentRepository;
import com.gucardev.ermanytomany.simple.category.Category;
import com.gucardev.ermanytomany.simple.category.CategoryRepository;
import com.gucardev.ermanytomany.simple.product.Product;
import com.gucardev.ermanytomany.simple.product.ProductRepository;
import com.gucardev.ermanytomany.simple.product.ProductService;
import com.gucardev.ermanytomany.simple.product.dto.ProductResponse;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Proves the read strategies by counting the SQL statements Hibernate actually executes. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryCountTest {

    private static final int ITEMS = 30;

    @Autowired
    private ProductService productService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics stats;
    private Long courseId;

    @BeforeEach
    void seed() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();

        Category books = new Category();
        books.setName("Books");
        Category sale = new Category();
        sale.setName("Sale");
        categoryRepository.saveAll(List.of(books, sale));
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < ITEMS; i++) {
            Product product = new Product();
            product.setName("Product %02d".formatted(i));
            product.setPrice(BigDecimal.TEN);
            product.addCategory(books);
            product.addCategory(sale);
            products.add(product);
        }
        productRepository.saveAll(products);

        Course course = new Course();
        course.setTitle("Math");
        courseRepository.save(course);
        courseId = course.getId();
        List<Student> students = new ArrayList<>();
        for (int i = 0; i < ITEMS; i++) {
            Student student = new Student();
            student.setName("Student %02d".formatted(i));
            student.enroll(course, "AA", LocalDate.now());
            students.add(student);
        }
        studentRepository.saveAll(students);

        stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
    }

    @Test
    void productListLoadsCategoriesInOneBatchInsteadOfNPlusOne() {
        Page<ProductResponse> page = productService.list(null, PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getContent()).allSatisfy(p -> assertThat(p.categories()).hasSize(2));
        // products page + count + ONE batched categories query (naive lazy loading: 1 + 1 + 20)
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void enrollmentRosterIsADtoProjectionWithoutEntities() {
        Page<EnrollmentResponse> page = enrollmentService.listByCourse(courseId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getTotalElements()).isEqualTo(ITEMS);
        // course existence check + rows + count; no Student/Course/Enrollment entity is materialized
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
        assertThat(stats.getEntityLoadCount()).isZero();
    }
}
