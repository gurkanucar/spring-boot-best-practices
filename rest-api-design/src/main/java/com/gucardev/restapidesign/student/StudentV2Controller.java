package com.gucardev.restapidesign.student;

import com.gucardev.restapidesign.student.dto.StudentV2Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Topic 3 — a breaking v2 response shape coexisting with v1, backed by the same store. */
@RestController
@RequestMapping("/api/v2/students")
public class StudentV2Controller {

    private final StudentStore store;

    public StudentV2Controller(StudentStore store) {
        this.store = store;
    }

    @GetMapping("/{id}")
    public StudentV2Response getById(@PathVariable Long id) {
        return StudentV2Response.from(store.findByIdOrThrow(id));
    }
}
