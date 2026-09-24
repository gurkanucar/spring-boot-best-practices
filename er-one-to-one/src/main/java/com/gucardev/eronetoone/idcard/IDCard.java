package com.gucardev.eronetoone.idcard;

import com.gucardev.eronetoone.person.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Owning side of the relationship: the {@code id_card.person_id} column is the foreign key. */
@Getter
@Setter
@Entity
public class IDCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String cardNumber;

    @Column(nullable = false)
    private LocalDate expiryDate;

    // unique = true is what turns the FK from many-to-one into a real one-to-one at the DB level.
    // Prefer Person.assignIdCard/removeIdCard over calling setPerson directly, so both sides stay in sync.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;
}
