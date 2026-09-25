package com.gucardev.resillience4j.fakeapi;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FakeApiRegistry {

    private final Map<FakeApi, FakeApiState> states = new EnumMap<>(FakeApi.class);
    /** The fake payment gateway's ledger: one charge per Idempotency-Key. */
    private final Map<String, Map<String, Object>> charges = new ConcurrentHashMap<>();

    public FakeApiRegistry() {
        for (FakeApi api : FakeApi.values()) {
            // Rendering a PDF is slow even when everything is fine; that is what the bulkhead protects.
            states.put(api, new FakeApiState(api == FakeApi.INVOICES ? 1000 : 0));
        }
    }

    public FakeApiState state(FakeApi api) {
        return states.get(api);
    }

    public Map<String, Map<String, Object>> charges() {
        return charges;
    }

    public Map<FakeApi, FakeApiState.Snapshot> snapshot() {
        Map<FakeApi, FakeApiState.Snapshot> result = new LinkedHashMap<>();
        states.forEach((api, state) -> result.put(api, state.snapshot()));
        return result;
    }

    public void resetAll() {
        states.values().forEach(FakeApiState::reset);
        charges.clear();
    }
}
