package com.gucardev.eronetoone.idcard;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IDCardRepository extends JpaRepository<IDCard, Long> {

    Optional<IDCard> findByCardNumber(String cardNumber);

    boolean existsByCardNumber(String cardNumber);

    boolean existsByCardNumberAndIdNot(String cardNumber, Long id);
}
