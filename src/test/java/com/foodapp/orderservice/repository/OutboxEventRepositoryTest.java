package com.foodapp.orderservice.repository;

import com.foodapp.orderservice.domain.entity.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class OutboxEventRepositoryTest {

    @Autowired OutboxEventRepository outboxRepository;

    @Test
    void shouldReturnOnlyUnprocessedEvents() {
        outboxRepository.save(buildEvent(false, 0));
        outboxRepository.save(buildEvent(true, 0));  // already processed — must be excluded

        var results = outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isProcessed()).isFalse();
    }

    @Test
    void shouldExcludeEventsAtOrAboveMaxRetryCount() {
        outboxRepository.save(buildEvent(false, 4)); // retryCount=4 < 5 → included
        outboxRepository.save(buildEvent(false, 5)); // retryCount=5 = maxRetries → excluded
        outboxRepository.save(buildEvent(false, 6)); // retryCount=6 > maxRetries → excluded

        var results = outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRetryCount()).isEqualTo(4);
    }

    @Test
    void shouldReturnEventsOrderedByCreatedAtAscending() throws InterruptedException {
        var first = buildEventAt(false, 0, LocalDateTime.now().minusMinutes(10));
        var second = buildEventAt(false, 0, LocalDateTime.now().minusMinutes(5));
        var third = buildEventAt(false, 0, LocalDateTime.now());

        // Save out of order intentionally
        outboxRepository.save(third);
        outboxRepository.save(first);
        outboxRepository.save(second);

        var results = outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getCreatedAt()).isBefore(results.get(1).getCreatedAt());
        assertThat(results.get(1).getCreatedAt()).isBefore(results.get(2).getCreatedAt());
    }

    @Test
    void shouldReturnEmptyWhenAllEventsAreProcessed() {
        outboxRepository.save(buildEvent(true, 0));
        outboxRepository.save(buildEvent(true, 2));

        var results = outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenRepositoryIsEmpty() {
        var results = outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnEventsForDifferentTopics() {
        outboxRepository.save(buildEventWithTopic(false, 0, "order.confirmed"));
        outboxRepository.save(buildEventWithTopic(false, 0, "order.cancelled"));

        var results = outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(OutboxEvent::getTopic)
                .containsExactlyInAnyOrder("order.confirmed", "order.cancelled");
    }

    private OutboxEvent buildEvent(boolean processed, int retryCount) {
        return buildEventAt(processed, retryCount, LocalDateTime.now());
    }

    private OutboxEvent buildEventAt(boolean processed, int retryCount, LocalDateTime createdAt) {
        return OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("ORDER_CONFIRMED")
                .topic("order.confirmed")
                .payload("{\"test\":\"data\"}")
                .createdAt(createdAt)
                .processed(processed)
                .retryCount(retryCount)
                .build();
    }

    private OutboxEvent buildEventWithTopic(boolean processed, int retryCount, String topic) {
        return OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("ORDER_EVENT")
                .topic(topic)
                .payload("{\"test\":\"data\"}")
                .createdAt(LocalDateTime.now())
                .processed(processed)
                .retryCount(retryCount)
                .build();
    }
}
