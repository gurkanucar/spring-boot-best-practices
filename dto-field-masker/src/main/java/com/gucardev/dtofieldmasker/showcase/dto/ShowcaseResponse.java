package com.gucardev.dtofieldmasker.showcase.dto;

import com.gucardev.dtofieldmasker.masking.MaskData;
import com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption;

/** Every option applied to the same input, so the output can be compared side by side. */
public record ShowcaseResponse(

        String original,

        /* Annotation defaults: value = 3, replaceChar = "x", LAST_X_CHARS_CLEAR */
        @MaskData
        String defaults,

        @MaskData(maskingOption = MaskingOption.FIRST_X_CHARS_CLEAR, value = 4)
        String firstXClear,

        @MaskData(maskingOption = MaskingOption.FIRST_X_CHARS_MASKED, value = 4)
        String firstXMasked,

        @MaskData(maskingOption = MaskingOption.LAST_X_CHARS_CLEAR, value = 4)
        String lastXClear,

        @MaskData(maskingOption = MaskingOption.LAST_X_CHARS_MASKED, value = 4)
        String lastXMasked,

        @MaskData(replaceChar = "*", maskingOption = MaskingOption.LAST_X_CHARS_CLEAR, value = 4)
        String customReplaceChar,

        @MaskData(replaceChar = "[#]", maskingOption = MaskingOption.FIRST_X_CHARS_CLEAR, value = 12)
        String multiCharReplacement,

        /* value 4 clear characters on a 3-character input would reveal everything: masked entirely. */
        @MaskData(maskingOption = MaskingOption.LAST_X_CHARS_CLEAR, value = 4)
        String shortValueFailsClosed) {
}
