package com.gucardev.dtofieldmasker.customer.dto;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.gucardev.dtofieldmasker.masking.MaskData;
import com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A domain-specific shortcut: "id numbers always have their first 4 characters masked".
 * Change the rule here once and every DTO using it follows.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@MaskData(maskingOption = MaskingOption.FIRST_X_CHARS_MASKED, value = 4)
public @interface MaskIdNumber {
}
