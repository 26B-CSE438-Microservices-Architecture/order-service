package com.foodapp.orderservice.event;

import com.foodapp.orderservice.config.scheduler.OutboxRelayScheduler;
import com.foodapp.orderservice.domain.entity.OutboxEvent;
import com.foodapp.orderservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock OutboxEventRepository outboxRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks OutboxRelayScheduler scheduler;

    @Test
    void shouldPublishPendingEventsAndMarkAsProcessed() throws Exception {
        var event = buildEvent("order.confirmed", 0);
        when(outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5))
                .thenReturn(List.of(event));

        // BURASI DEĞİŞTİ: void metot olduğu için doNothing() kullanıyoruz
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString());

        scheduler.processOutbox();

        assertThat(event.isProcessed()).isTrue();
        verify(outboxRepository).save(event);
    }

    @Test
    void shouldIncrementRetryCountOnRabbitMQFailure() {
        var event = buildEvent("order.confirmed", 0);
        when(outboxRepository.findByProcessedFalseAndRetryCountLessThanOrderByCreatedAtAsc(5))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("RabbitMQ bağlantı hatası"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString());

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
        doThrow(new RuntimeException("İlk event hata"))
                .when(rabbitTemplate).convertAndSend(eq("order.confirmed"), anyString());
        
        // BURASI DEĞİŞTİ: ikinci event başarılı (void metot)
        doNothing().when(rabbitTemplate).convertAndSend(eq("order.cancelled"), anyString());

        scheduler.processOutbox();

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

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
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
