package com.gucardev.eronetoone.idcard;

import com.gucardev.eronetoone.idcard.dto.IDCardRequest;
import com.gucardev.eronetoone.idcard.dto.IDCardResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IDCardController {

    private final IDCardService idCardService;

    @GetMapping("/api/persons/{personId}/id-card")
    public IDCardResponse get(@PathVariable Long personId) {
        return idCardService.getByPersonId(personId);
    }

    @PutMapping("/api/persons/{personId}/id-card")
    public ResponseEntity<IDCardResponse> upsert(@PathVariable Long personId,
                                                 @Valid @RequestBody IDCardRequest request) {
        IDCardService.UpsertResult result = idCardService.upsert(personId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.card());
    }

    @DeleteMapping("/api/persons/{personId}/id-card")
    public ResponseEntity<Void> delete(@PathVariable Long personId) {
        idCardService.deleteByPersonId(personId);
        return ResponseEntity.noContent().build();
    }

    /** Navigating from the owning side: card number to card (and the owner id). */
    @GetMapping("/api/id-cards/{cardNumber}")
    public IDCardResponse getByCardNumber(@PathVariable String cardNumber) {
        return idCardService.getByCardNumber(cardNumber);
    }
}
