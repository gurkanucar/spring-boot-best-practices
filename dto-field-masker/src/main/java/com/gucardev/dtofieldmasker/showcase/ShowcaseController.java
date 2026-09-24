package com.gucardev.dtofieldmasker.showcase;

import com.gucardev.dtofieldmasker.showcase.dto.ShowcaseResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/showcase")
public class ShowcaseController {

    private static final String SAMPLE = "1234567890123456";

    @GetMapping
    public ShowcaseResponse showcase() {
        // "original" is deliberately not annotated: it shows the input the other fields start from.
        return new ShowcaseResponse(SAMPLE, SAMPLE, SAMPLE, SAMPLE, SAMPLE, SAMPLE, SAMPLE, SAMPLE, "abc");
    }
}
