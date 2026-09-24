package com.gucardev.eronetoone.person;

import com.gucardev.eronetoone.common.error.ConflictException;
import com.gucardev.eronetoone.common.error.ResourceNotFoundException;
import com.gucardev.eronetoone.idcard.IDCardRepository;
import com.gucardev.eronetoone.person.dto.CreatePersonRequest;
import com.gucardev.eronetoone.person.dto.PersonResponse;
import com.gucardev.eronetoone.person.dto.UpdatePersonRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonService {

    private final PersonRepository personRepository;
    private final IDCardRepository idCardRepository;

    @Transactional
    public PersonResponse create(CreatePersonRequest request) {
        if (request.idCard() != null && idCardRepository.existsByCardNumber(request.idCard().cardNumber())) {
            throw new ConflictException("Card number already in use: " + request.idCard().cardNumber());
        }
        Person saved = personRepository.save(PersonMapper.toEntity(request));
        return PersonMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PersonResponse get(Long id) {
        return PersonMapper.toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<PersonResponse> list(Pageable pageable) {
        return personRepository.findAll(pageable).map(PersonMapper::toResponse);
    }

    @Transactional
    public PersonResponse update(Long id, UpdatePersonRequest request) {
        Person person = getEntity(id);
        person.setName(request.name());
        return PersonMapper.toResponse(person);
    }

    @Transactional
    public void delete(Long id) {
        personRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Person getEntity(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found: " + id));
    }
}
