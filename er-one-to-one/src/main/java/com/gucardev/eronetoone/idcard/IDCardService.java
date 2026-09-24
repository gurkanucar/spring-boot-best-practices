package com.gucardev.eronetoone.idcard;

import com.gucardev.eronetoone.common.error.ConflictException;
import com.gucardev.eronetoone.common.error.ResourceNotFoundException;
import com.gucardev.eronetoone.idcard.dto.IDCardRequest;
import com.gucardev.eronetoone.idcard.dto.IDCardResponse;
import com.gucardev.eronetoone.person.Person;
import com.gucardev.eronetoone.person.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IDCardService {

    private final IDCardRepository idCardRepository;
    private final PersonService personService;

    public record UpsertResult(IDCardResponse card, boolean created) {
    }

    @Transactional(readOnly = true)
    public IDCardResponse getByPersonId(Long personId) {
        return IDCardMapper.toResponse(requireCard(personService.getEntity(personId), personId));
    }

    @Transactional(readOnly = true)
    public IDCardResponse getByCardNumber(String cardNumber) {
        return idCardRepository.findByCardNumber(cardNumber)
                .map(IDCardMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + cardNumber));
    }

    /** Creates the person's card, or updates it in place if one already exists. */
    @Transactional
    public UpsertResult upsert(Long personId, IDCardRequest request) {
        Person person = personService.getEntity(personId);
        IDCard existing = person.getIdCard();

        // Updating in place (instead of remove + assign a new card) matters: Hibernate flushes
        // inserts before deletes, so swapping cards would briefly violate the unique person_id.
        if (existing != null) {
            if (idCardRepository.existsByCardNumberAndIdNot(request.cardNumber(), existing.getId())) {
                throw new ConflictException("Card number already in use: " + request.cardNumber());
            }
            IDCardMapper.apply(existing, request);
            return new UpsertResult(IDCardMapper.toResponse(existing), false);
        }

        if (idCardRepository.existsByCardNumber(request.cardNumber())) {
            throw new ConflictException("Card number already in use: " + request.cardNumber());
        }
        IDCard card = IDCardMapper.toEntity(request);
        person.assignIdCard(card);
        idCardRepository.saveAndFlush(card);
        return new UpsertResult(IDCardMapper.toResponse(card), true);
    }

    @Transactional
    public void deleteByPersonId(Long personId) {
        Person person = personService.getEntity(personId);
        requireCard(person, personId);
        person.removeIdCard();
    }

    private IDCard requireCard(Person person, Long personId) {
        if (person.getIdCard() == null) {
            throw new ResourceNotFoundException("Person " + personId + " has no ID card");
        }
        return person.getIdCard();
    }
}
