package com.gucardev.eronetoone.idcard;

import com.gucardev.eronetoone.idcard.dto.IDCardRequest;
import com.gucardev.eronetoone.idcard.dto.IDCardResponse;

public final class IDCardMapper {

    private IDCardMapper() {
    }

    public static IDCard toEntity(IDCardRequest request) {
        IDCard card = new IDCard();
        apply(card, request);
        return card;
    }

    public static void apply(IDCard card, IDCardRequest request) {
        card.setCardNumber(request.cardNumber());
        card.setExpiryDate(request.expiryDate());
    }

    public static IDCardResponse toResponse(IDCard card) {
        // getPerson() may be a lazy proxy; reading its id does not trigger a query.
        return new IDCardResponse(card.getId(), card.getCardNumber(), card.getExpiryDate(),
                card.getPerson().getId());
    }
}
