package com.gucardev.eronetoone.person;

import com.gucardev.eronetoone.idcard.IDCard;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Inverse side of the relationship: owns no column, just mirrors {@code IDCard.person}. */
@Getter
@Setter
@Entity
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // cascade ALL: saving/deleting a Person saves/deletes its card.
    // orphanRemoval: dropping the reference (removeIdCard) deletes the card row.
    @Setter(AccessLevel.NONE)
    @OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private IDCard idCard;

    /** Attaches a card and keeps both sides of the association consistent. */
    public void assignIdCard(IDCard card) {
        if (card == null) {
            removeIdCard();
            return;
        }
        card.setPerson(this);
        this.idCard = card;
    }

    /** Detaches the current card (if any); orphanRemoval deletes it on flush. */
    public void removeIdCard() {
        if (this.idCard != null) {
            this.idCard.setPerson(null);
            this.idCard = null;
        }
    }
}
