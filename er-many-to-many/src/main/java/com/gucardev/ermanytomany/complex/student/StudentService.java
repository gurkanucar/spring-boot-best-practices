package com.gucardev.ermanytomany.complex.student;

import com.gucardev.ermanytomany.common.error.ResourceNotFoundException;
import com.gucardev.ermanytomany.complex.student.dto.StudentRequest;
import com.gucardev.ermanytomany.complex.student.dto.StudentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    @Transactional
    public StudentResponse create(StudentRequest request) {
        return StudentMapper.toResponse(studentRepository.save(StudentMapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return StudentMapper.toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> list(Pageable pageable) {
        return studentRepository.findAll(pageable).map(StudentMapper::toResponse);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = getEntity(id);
        student.setName(request.name());
        return StudentMapper.toResponse(student);
    }

    /** Cascades to the student's enrollments; the courses themselves stay. */
    @Transactional
    public void delete(Long id) {
        studentRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Student getEntity(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + id));
    }

    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (!studentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Student not found: " + id);
        }
    }
}
