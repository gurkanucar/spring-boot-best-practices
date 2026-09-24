package com.gucardev.eronetoone.person;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.eronetoone.idcard.IDCard;
import org.junit.jupiter.api.Test;

class PersonTest {

    @Test
    void assignIdCardSetsBothSides() {
        Person person = new Person();
        IDCard card = new IDCard();

        person.assignIdCard(card);

        assertThat(person.getIdCard()).isSameAs(card);
        assertThat(card.getPerson()).isSameAs(person);
    }

    @Test
    void removeIdCardClearsBothSides() {
        Person person = new Person();
        IDCard card = new IDCard();
        person.assignIdCard(card);

        person.removeIdCard();

        assertThat(person.getIdCard()).isNull();
        assertThat(card.getPerson()).isNull();
    }

    @Test
    void assignNullRemovesTheCard() {
        Person person = new Person();
        IDCard card = new IDCard();
        person.assignIdCard(card);

        person.assignIdCard(null);

        assertThat(person.getIdCard()).isNull();
        assertThat(card.getPerson()).isNull();
    }
}
