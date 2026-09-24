package com.gucardev.dtofieldmasker.customer.dto;

import com.gucardev.dtofieldmasker.masking.MaskData;
import com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption;

public record AccountResponse(

        String accountName,

        @MaskData(replaceChar = "*", maskingOption = MaskingOption.LAST_X_CHARS_MASKED, value = 10)
        String accountNumber) {
}
