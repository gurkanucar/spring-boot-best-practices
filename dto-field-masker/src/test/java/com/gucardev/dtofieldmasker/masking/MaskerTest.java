package com.gucardev.dtofieldmasker.masking;

import static com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption.FIRST_X_CHARS_CLEAR;
import static com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption.FIRST_X_CHARS_MASKED;
import static com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption.LAST_X_CHARS_CLEAR;
import static com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption.LAST_X_CHARS_MASKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaskerTest {

    @ParameterizedTest(name = "{0} x={1} on 123456789 -> {2}")
    @CsvSource({
            "FIRST_X_CHARS_CLEAR,  4, 1234xxxxx",
            "FIRST_X_CHARS_MASKED, 4, xxxx56789",
            "LAST_X_CHARS_CLEAR,   4, xxxxx6789",
            "LAST_X_CHARS_MASKED,  4, 12345xxxx",
    })
    void appliesEachOption(MaskingOption option, int count, String expected) {
        assertThat(Masker.mask("123456789", count, "x", option)).isEqualTo(expected);
    }

    @Test
    void matchesTheDocumentedAccountNumberExample() {
        assertThat(Masker.mask("123456789012345", 10, "*", LAST_X_CHARS_MASKED)).isEqualTo("12345**********");
    }

    @Test
    void zeroCountMeansNothingMaskedForMaskedOptionsAndEverythingForClearOptions() {
        assertThat(Masker.mask("1234", 0, "x", FIRST_X_CHARS_MASKED)).isEqualTo("1234");
        assertThat(Masker.mask("1234", 0, "x", LAST_X_CHARS_MASKED)).isEqualTo("1234");
        assertThat(Masker.mask("1234", 0, "x", FIRST_X_CHARS_CLEAR)).isEqualTo("xxxx");
        assertThat(Masker.mask("1234", 0, "x", LAST_X_CHARS_CLEAR)).isEqualTo("xxxx");
    }

    @Test
    void clearOptionsFailClosedWhenTheWholeValueWouldBeRevealed() {
        assertThat(Masker.mask("abc", 4, "x", LAST_X_CHARS_CLEAR)).isEqualTo("xxx");
        assertThat(Masker.mask("abc", 3, "x", FIRST_X_CHARS_CLEAR)).isEqualTo("xxx");
        // one character fewer than the length still reveals what was asked for
        assertThat(Masker.mask("abcd", 3, "x", LAST_X_CHARS_CLEAR)).isEqualTo("xbcd");
    }

    @Test
    void maskedOptionsMaskEverythingWhenCountCoversTheValue() {
        assertThat(Masker.mask("abc", 3, "x", FIRST_X_CHARS_MASKED)).isEqualTo("xxx");
        assertThat(Masker.mask("abc", 99, "x", LAST_X_CHARS_MASKED)).isEqualTo("xxx");
    }

    @Test
    void emptyValueStaysEmpty() {
        assertThat(Masker.mask("", 3, "x", LAST_X_CHARS_CLEAR)).isEmpty();
    }

    @Test
    void replacementMayBeLongerThanOneCharacter() {
        assertThat(Masker.mask("12345", 2, "[#]", FIRST_X_CHARS_MASKED)).isEqualTo("[#][#]345");
    }

    @Test
    void countsUnicodeCodePointsNotChars() {
        // each emoji is two UTF-16 chars but one character to a reader
        assertThat(Masker.mask("😀😀😀😀😀", 2, "x", LAST_X_CHARS_CLEAR)).isEqualTo("xxx😀😀");
    }

    @Test
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> Masker.mask("1234", -1, "x", LAST_X_CHARS_CLEAR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
