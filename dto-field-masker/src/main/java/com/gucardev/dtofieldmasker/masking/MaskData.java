package com.gucardev.dtofieldmasker.masking;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Masks a String field of a DTO when it is written as JSON.
 *
 * <pre>{@code
 * @MaskData(replaceChar = "*", maskingOption = MaskingOption.LAST_X_CHARS_MASKED, value = 10)
 * private String accountNumber;      // "123456789012345" -> "12345**********"
 * }</pre>
 *
 * Because it is meta-annotated with {@code @JacksonAnnotationsInside}, it can also be used as a
 * building block for your own, shorter annotations (see {@code MaskIdNumber}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@JsonSerialize(using = MaskDataSerializer.class)
public @interface MaskData {

    int DEFAULT_VALUE = 3;
    String DEFAULT_REPLACE_CHAR = "x";

    /** How many characters the {@link #maskingOption()} refers to. Must not be negative. */
    int value() default DEFAULT_VALUE;

    /** What each masked character is replaced with. */
    String replaceChar() default DEFAULT_REPLACE_CHAR;

    MaskingOption maskingOption() default MaskingOption.LAST_X_CHARS_CLEAR;

    enum MaskingOption {
        /** The first {@code value} characters stay readable, the rest is masked. */
        FIRST_X_CHARS_CLEAR,
        /** The first {@code value} characters are masked, the rest stays readable. */
        FIRST_X_CHARS_MASKED,
        /** The last {@code value} characters stay readable, the rest is masked. */
        LAST_X_CHARS_CLEAR,
        /** The last {@code value} characters are masked, the rest stays readable. */
        LAST_X_CHARS_MASKED
    }
}
