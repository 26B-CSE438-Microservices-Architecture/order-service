package com.foodapp.orderservice.gateway.feign;

import com.foodapp.orderservice.gateway.RestaurantGateway;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantFeignGateway implements RestaurantGateway {

    private final RestaurantFeignClient feignClient;

    @Override
    @CircuitBreaker(name = "restaurantService", fallbackMethod = "validateItemsFallback")
    @Retry(name = "restaurantService")
    public MenuValidationResult validateOrderItems(UUID restaurantId, List<OrderItemRequest> items) {
        return feignClient.validateItems(restaurantId, items);
    }

    // Fallback Metodu (Servis çökerse veya devre kesici açılırsa burası çalışır)
    public MenuValidationResult validateItemsFallback(UUID restaurantId, List<OrderItemRequest> items, Throwable t) {
        log.error("Circuit Breaker OPEN or API Failed for validateItems. RestaurantId: {}. Reason: {}", restaurantId, t.getMessage());
        return new MenuValidationResult(false, List.of(), "Restoran servisine şu anda ulaşılamıyor, lütfen daha sonra tekrar deneyin.");
    }

    @Override
    public boolean isRestaurantOpen(UUID restaurantId) {
        return feignClient.isOpen(restaurantId);
    }

    // Fallback Metodu
    public boolean isOpenFallback(UUID restaurantId, Throwable t) {
        log.error("Circuit Breaker OPEN or API Failed for isRestaurantOpen. RestaurantId: {}. Reason: {}", restaurantId, t.getMessage());
        return false; // Servis kapalıysa veya yanıt vermiyorsa güvenli yol olarak kapalı dönüyoruz.
    }

    @FeignClient(name = "restaurant-service", url = "${restaurant.service.url:http://restaurant-service}")
    interface RestaurantFeignClient {
        @PostMapping("/internal/restaurants/{restaurantId}/validate-items")
        MenuValidationResult validateItems(@PathVariable("restaurantId") UUID restaurantId,
                                           @RequestBody List<OrderItemRequest> items);

        @GetMapping("/internal/restaurants/{restaurantId}/is-open")
        boolean isOpen(@PathVariable("restaurantId") UUID restaurantId);
    }
}