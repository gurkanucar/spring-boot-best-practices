package com.gucardev.eronetoone.person;

import com.gucardev.eronetoone.idcard.IDCardMapper;
import com.gucardev.eronetoone.person.dto.CreatePersonRequest;
import com.gucardev.eronetoone.person.dto.PersonResponse;

public final class PersonMapper {

    private PersonMapper() {
    }

    public static Person toEntity(CreatePersonRequest request) {
        Person person = new Person();
        person.setName(request.name());
        if (request.idCard() != null) {
            person.assignIdCard(IDCardMapper.toEntity(request.idCard()));
        }
        return person;
    }

    public static PersonResponse toResponse(Person person) {
        return new PersonResponse(person.getId(), person.getName(),
                person.getIdCard() == null ? null : IDCardMapper.toResponse(person.getIdCard()));
    }
}
