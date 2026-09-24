package com.gucardev.ermanytomany.complex.enrollment;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Composite primary key of Enrollment for {@code @IdClass}. Field names must match the
 * {@code @Id} fields of the entity ({@code student}, {@code course}); their types are the
 * primary key types of the referenced entities, not the entities themselves.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EnrollmentId implements Serializable {

    private Long student;
    private Long course;
}
