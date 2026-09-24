package com.gucardev.ermanytomany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.ermanytomany.complex.course.CourseRepository;
import com.gucardev.ermanytomany.complex.enrollment.EnrollmentRepository;
import com.gucardev.ermanytomany.complex.student.StudentRepository;
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
class ComplexManyToManyApiTest {

    private static final String STUDENTS = "/api/complex/students";
    private static final String COURSES = "/api/complex/courses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @BeforeEach
    void clean() {
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private long createStudent(String name) throws Exception {
        return idOf(mockMvc.perform(post(STUDENTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn());
    }

    private long createCourse(String title) throws Exception {
        return idOf(mockMvc.perform(post(COURSES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\"}".formatted(title)))
                .andExpect(status().isCreated()).andReturn());
    }

    private void enroll(long studentId, long courseId, String grade) throws Exception {
        String body = grade == null ? "{\"courseId\": %d}".formatted(courseId)
                : "{\"courseId\": %d, \"grade\": \"%s\"}".formatted(courseId, grade);
        mockMvc.perform(post(STUDENTS + "/" + studentId + "/enrollments")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void studentAndCourseCrud() throws Exception {
        long student = createStudent("Ada");
        long course = createCourse("Math");

        mockMvc.perform(put(STUDENTS + "/" + student).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ada Lovelace\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ada Lovelace"));
        mockMvc.perform(put(COURSES + "/" + course).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Advanced Math\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Advanced Math"));
        mockMvc.perform(get(STUDENTS)).andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get(COURSES)).andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(post(STUDENTS).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(STUDENTS + "/999")).andExpect(status().isNotFound());
        mockMvc.perform(get(COURSES + "/999")).andExpect(status().isNotFound());
    }

    @Test
    void enrollsAStudentInACourse() throws Exception {
        long student = createStudent("Ada");
        long course = createCourse("Math");

        mockMvc.perform(post(STUDENTS + "/" + student + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": %d, \"grade\": \"AA\"}".formatted(course)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/complex/students/%d/enrollments/%d".formatted(student, course)))
                .andExpect(jsonPath("$.studentName").value("Ada"))
                .andExpect(jsonPath("$.courseTitle").value("Math"))
                .andExpect(jsonPath("$.grade").value("AA"))
                .andExpect(jsonPath("$.enrolledAt").isNotEmpty());
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateEnrollmentAndUnknownIds() throws Exception {
        long student = createStudent("Ada");
        long course = createCourse("Math");
        enroll(student, course, null);

        mockMvc.perform(post(STUDENTS + "/" + student + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": %d}".formatted(course)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(STUDENTS + "/" + student + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": 999}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(STUDENTS + "/999/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": %d}".formatted(course)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(STUDENTS + "/" + student + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    void sameStudentCanTakeManyCoursesAndCourseHasManyStudents() throws Exception {
        long ada = createStudent("Ada");
        long bob = createStudent("Bob");
        long math = createCourse("Math");
        long art = createCourse("Art");
        enroll(ada, math, "AA");
        enroll(ada, art, "BB");
        enroll(bob, math, null);

        mockMvc.perform(get(STUDENTS + "/" + ada + "/enrollments?sort=grade"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].grade").value("AA"));
        mockMvc.perform(get(COURSES + "/" + math + "/enrollments"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(STUDENTS + "/999/enrollments")).andExpect(status().isNotFound());
        mockMvc.perform(get(COURSES + "/999/enrollments")).andExpect(status().isNotFound());
    }

    @Test
    void readsAndGradesASingleEnrollment() throws Exception {
        long student = createStudent("Ada");
        long course = createCourse("Math");
        enroll(student, course, null);
        String url = STUDENTS + "/" + student + "/enrollments/" + course;

        mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.grade").doesNotExist());
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"grade\": \"BA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grade").value("BA"));
        mockMvc.perform(get(url)).andExpect(jsonPath("$.grade").value("BA"));
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"grade\": \"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(STUDENTS + "/" + student + "/enrollments/999")).andExpect(status().isNotFound());
    }

    @Test
    void unenrollingKeepsStudentAndCourse() throws Exception {
        long student = createStudent("Ada");
        long course = createCourse("Math");
        enroll(student, course, "AA");
        String url = STUDENTS + "/" + student + "/enrollments/" + course;

        mockMvc.perform(delete(url)).andExpect(status().isNoContent());

        assertThat(enrollmentRepository.count()).isZero();
        assertThat(studentRepository.count()).isEqualTo(1);
        assertThat(courseRepository.count()).isEqualTo(1);
        mockMvc.perform(delete(url)).andExpect(status().isNotFound());
        // can enroll again after leaving
        enroll(student, course, null);
    }

    @Test
    void deletingStudentCascadesToEnrollmentsButKeepsCourses() throws Exception {
        long student = createStudent("Ada");
        long math = createCourse("Math");
        long art = createCourse("Art");
        enroll(student, math, null);
        enroll(student, art, null);

        mockMvc.perform(delete(STUDENTS + "/" + student)).andExpect(status().isNoContent());

        assertThat(enrollmentRepository.count()).isZero();
        assertThat(courseRepository.count()).isEqualTo(2);
    }

    @Test
    void deletingCourseCascadesToEnrollmentsButKeepsStudents() throws Exception {
        long ada = createStudent("Ada");
        long bob = createStudent("Bob");
        long math = createCourse("Math");
        enroll(ada, math, null);
        enroll(bob, math, null);

        mockMvc.perform(delete(COURSES + "/" + math)).andExpect(status().isNoContent());

        assertThat(enrollmentRepository.count()).isZero();
        assertThat(studentRepository.count()).isEqualTo(2);
    }
}
