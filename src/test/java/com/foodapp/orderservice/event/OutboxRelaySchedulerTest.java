package com.foodapp.orderservice.event;

import com.foodapp.orderservice.config.scheduler.OutboxRelayScheduler;
import com.foodapp.orderservice.domain.entity.OutboxEvent;
import com.foodapp.orderservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.concurrent.ListenableFuture;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock OutboxEventRepository outboxRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks OutboxRelayScheduler scheduler;

    @Test
    void shouldPublishPendingEventsAndMarkAsProcessed() throws Exception {
        var event = buildEvent("order.confirmed", 0);
        when(outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5))
                .thenReturn(List.of(event));

        var future = CompletableFuture.completedFuture(null);
        doReturn(future).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        scheduler.processOutbox();

        assertThat(event.isProcessed()).isTrue();
        verify(outboxRepository).save(event);
    }

    @Test
    void shouldIncrementRetryCountOnKafkaFailure() {
        var event = buildEvent("order.confirmed", 0);
        when(outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka bağlantı hatası"));

        scheduler.processOutbox();

        assertThat(event.isProcessed()).isFalse();
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(outboxRepository).save(event);
    }

    @Test
    void shouldContinueProcessingOtherEventsAfterOneFailure() throws Exception {
        var failingEvent = buildEvent("order.confirmed", 0);
        var successEvent = buildEvent("order.cancelled", 0);

        when(outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5))
                .thenReturn(List.of(failingEvent, successEvent));

        // ilk event hata verir
        when(kafkaTemplate.send(eq("order.confirmed"), anyString(), anyString()))
                .thenThrow(new RuntimeException("İlk event hata"));
        // ikinci event başarılı
        var future = CompletableFuture.completedFuture(null);
        doReturn(future).when(kafkaTemplate).send(eq("order.cancelled"), anyString(), anyString());

        scheduler.processOutbox();

        // Her iki event de save edilmeli (biri başarısız retryCount++ ile, diğeri processed=true)
        verify(outboxRepository, times(2)).save(any());
        assertThat(failingEvent.isProcessed()).isFalse();
        assertThat(successEvent.isProcessed()).isTrue();
    }

    @Test
    void shouldNotFetchEventsAtMaxRetryCount() {
        // Retry count 5'e ulaşmış eventler sorguya dahil edilmemeli
        when(outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5))
                .thenReturn(List.of()); // zaten filtre edilmiş

        scheduler.processOutbox();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private OutboxEvent buildEvent(String topic, int retryCount) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID().toString())
                .eventType("ORDER_CONFIRMED")
                .topic(topic)
                .payload("{\"test\":\"data\"}")
                .createdAt(LocalDateTime.now())
                .processed(false)
                .retryCount(retryCount)
                .build();
    }
}
