package com.gucardev.ermanytomany.complex.course;

import com.gucardev.ermanytomany.common.error.ResourceNotFoundException;
import com.gucardev.ermanytomany.complex.course.dto.CourseRequest;
import com.gucardev.ermanytomany.complex.course.dto.CourseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    @Transactional
    public CourseResponse create(CourseRequest request) {
        return CourseMapper.toResponse(courseRepository.save(CourseMapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public CourseResponse get(Long id) {
        return CourseMapper.toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<CourseResponse> list(Pageable pageable) {
        return courseRepository.findAll(pageable).map(CourseMapper::toResponse);
    }

    @Transactional
    public CourseResponse update(Long id, CourseRequest request) {
        Course course = getEntity(id);
        course.setTitle(request.title());
        return CourseMapper.toResponse(course);
    }

    /** Cascades to the course's enrollments; the students themselves stay. */
    @Transactional
    public void delete(Long id) {
        courseRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Course getEntity(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + id));
    }

    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (!courseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course not found: " + id);
        }
    }
}
