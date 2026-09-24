package com.gucardev.dtofieldmasker.customer.dto;

import java.util.List;

public record CustomerResponse(

        String name,

        @MaskIdNumber
        String idNumber,

        List<AccountResponse> accounts) {
}
