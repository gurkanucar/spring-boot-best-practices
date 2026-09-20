package com.gucardev.validation.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embeddable type: no table of its own, columns live in {@code users}.
 * Constraints apply both in the DDL and as pre-insert Hibernate checks.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @NotBlank
    @Column(name = "city", nullable = false, length = 60)
    private String city;

    @NotBlank
    @Column(name = "district", nullable = false, length = 60)
    private String district;

    @Pattern(regexp = "^\\d{5}$")
    @Column(name = "postal_code", nullable = false, length = 5)
    private String postalCode;
}
