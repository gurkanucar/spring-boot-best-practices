package com.gucardev.entityencryption.crypto;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo trigger. In a real system this belongs behind admin authorization or in a batch job. */
@RestController
@RequestMapping("/api/admin/key-rotation")
public class KeyRotationController {

    private final KeyRotationService service;

    public KeyRotationController(KeyRotationService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Integer> rotate() {
        return Map.of("reencryptedValues", service.rotate());
    }
}
