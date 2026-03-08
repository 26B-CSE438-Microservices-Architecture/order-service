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

    private final OutboxEventRepository outboxRepository;

    // HATA BURADAYDI: <String, String> yerine <String, Object> yaptık
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000) // Her 2 saniyede bir çalışır
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : pendingEvents) {
            try {
                String topic = event.getEventType().toLowerCase().replace("_", ".");

                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get(); // Senkron gönderim

                event.setProcessed(true);
                outboxRepository.save(event);
                log.debug("Outbox event published to Kafka: {}", event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id: {}", event.getId(), e);
                break; // Hata durumunda döngüyü kır, bir sonraki çalışmada tekrar dener
            }
        }
    }
}