package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.TaskHandler;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import tools.jackson.databind.json.JsonMapper;

class TaskRunnerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /** A handler that only exists to declare a payload type. */
    private abstract static class Handler<P> implements TaskHandler<P> {
        public TaskType type() {
            return TaskType.EMAIL_SEND;
        }

        public void handle(P payload) {
        }
    }

    record Export(Long reportRequestId, LocalDate from, LocalDate to, List<String> columns) {
    }

    /** Enqueue writes the payload as JSON; the runner reads it back as the handler's type. */
    private <P> Object roundTrip(Handler<P> handler, P payload) {
        String stored = jsonMapper.writeValueAsString(payload);
        return TaskRunner.payloadReader(handler, jsonMapper).readValue(stored);
    }

    @Test
    void payloadsComeBackAsTheHandlersType() {
        UUID uuid = UUID.randomUUID();
        var export = new Export(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of("name", "total"));

        assertThat(roundTrip(new Handler<Long>() {}, 42L)).isEqualTo(42L);
        assertThat(roundTrip(new Handler<UUID>() {}, uuid)).isEqualTo(uuid);
        assertThat(roundTrip(new Handler<String>() {}, "tr-TR")).isEqualTo("tr-TR");
        assertThat(roundTrip(new Handler<Export>() {}, export)).isEqualTo(export);
        // Generics are kept: small numbers still come back as Long, not Integer.
        assertThat(roundTrip(new Handler<List<Long>>() {}, List.of(1L, 2L)))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly(1L, 2L);
    }

    @Test
    void recordFieldsAreRequired() {
        var reader = TaskRunner.payloadReader(new Handler<Export>() {}, jsonMapper);
        assertThatThrownBy(() -> reader.readValue("""
                {"reportRequestId": 7, "from": "2026-09-01", "to": null, "columns": []}"""))
                .hasMessageContaining("'to'");
    }

    @Test
    void payloadTypeIsResolvedThroughAJdkProxy() {
        var proxy = new ProxyFactory(new Handler<List<Long>>() {});
        proxy.setInterfaces(TaskHandler.class);
        var reader = TaskRunner.payloadReader((TaskHandler<?>) proxy.getProxy(), jsonMapper);
        assertThat((List<?>) reader.readValue("[1,2]"))
                .isEqualTo(List.of(1L, 2L));
    }

    @Test
    void backoffGrowsFourfoldUpToOneHour() {
        assertThat(IntStream.rangeClosed(1, 6).mapToObj(TaskRunner::backoff).toList()).containsExactly(
                Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(8), Duration.ofMinutes(32),
                Duration.ofHours(1), Duration.ofHours(1));
    }
}
