package com.foodapp.orderservice.config.scheduler;

import com.foodapp.orderservice.domain.entity.OutboxEvent;
import com.foodapp.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pendingEvents =
                outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(MAX_RETRY_COUNT);

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get();

                event.setProcessed(true);
                outboxRepository.save(event);
                log.debug("Outbox event published: eventType={} topic={}", event.getEventType(), event.getTopic());
            } catch (Exception e) {
                int newRetryCount = event.getRetryCount() + 1;
                event.setRetryCount(newRetryCount);
                outboxRepository.save(event);
                log.error("Failed to publish outbox event id={} retryCount={}/{}. Will retry later.",
                        event.getId(), newRetryCount, MAX_RETRY_COUNT, e);
                // continue ile diğer event'ler etkilenmez
            }
        }
    }
}