package com.gucardev.resillience4j.fakeapi;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Breaks and repairs the fake APIs, and shows how many requests really reached them. */
@RestController
@RequestMapping("/fake-api/admin")
public class FakeApiAdminController {

    private final FakeApiRegistry registry;

    public FakeApiAdminController(FakeApiRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<FakeApi, FakeApiState.Snapshot> all() {
        return registry.snapshot();
    }

    /** Every field is optional: {@code {"failNext": 2}}, {@code {"down": true}}, {@code {"latencyMs": 3000}}. */
    @PutMapping("/{api}")
    public FakeApiState.Snapshot configure(@PathVariable String api, @RequestBody BehaviorRequest request) {
        FakeApiState state = registry.state(FakeApi.valueOf(api.toUpperCase()));
        state.configure(request.failNext(), request.down(), request.latencyMs());
        return state.snapshot();
    }

    @PostMapping("/reset")
    public Map<FakeApi, FakeApiState.Snapshot> reset() {
        registry.resetAll();
        return registry.snapshot();
    }

    public record BehaviorRequest(Integer failNext, Boolean down, Long latencyMs) {
    }
}
