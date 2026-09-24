package com.gucardev.eronetoone.person;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

    // The inverse side of a one-to-one cannot be lazy, so without a fetch join every listed
    // person triggers its own "select ... from idcard where person_id = ?" (N+1).
    @Override
    @EntityGraph(attributePaths = "idCard")
    Page<Person> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "idCard")
    Optional<Person> findById(Long id);
}
