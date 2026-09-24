package com.gucardev.kafkaoutboxpatternflightdataupdatesexample;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxKafkaSender;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The scheduled relay stays idle, so tests drive {@link OutboxPublisher} event by event.
 * The Kafka sender is a spy: real by default, stubbed to fail where a test needs a broken broker.
 */
@SpringBootTest(properties = {
        "app.outbox.poll-interval=1h",
        "app.outbox.retry-backoff=1h",
        "app.outbox.max-backoff=1h",
        "app.outbox.cleanup-cron=-"
})
public abstract class ManualRelayTestBase extends IntegrationTestBase {

    @MockitoSpyBean
    protected OutboxKafkaSender sender;
    @Autowired
    protected OutboxPublisher publisher;

    @BeforeEach
    void publishLeftoversOfOtherTests() {
        publishAvailable();
    }
    protected int publishAvailable() {
        int sent = 0;
        while (publisher.publishNext()) {
            sent++;
        }
        return sent;
    }
}
