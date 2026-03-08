package com.foodapp.orderservice.gateway.feign;

import com.foodapp.orderservice.domain.enums.PaymentMethod;
import com.foodapp.orderservice.domain.valueobject.Money;
import com.foodapp.orderservice.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFeignGateway implements PaymentGateway {

    private final PaymentFeignClient feignClient;

    @Override
    public PaymentInitiationResult initiatePayment(UUID orderId, UUID userId, Money amount, PaymentMethod paymentMethod) {
        var request = Map.of(
                "orderId", orderId.toString(),
                "userId", userId.toString(),
                "amount", amount.getAmount().toString(),
                "currency", amount.getCurrency(),
                "paymentMethod", paymentMethod.name()
        );
        try {
            return feignClient.initiatePayment(request);
        } catch (Exception e) {
            log.error("Payment initiation failed for order: {}", orderId, e);
            return new PaymentInitiationResult(null, "FAILED");
        }
    }

    @FeignClient(name = "payment-service", url = "${payment.service.url:http://payment-service}")
    interface PaymentFeignClient {
        @PostMapping("/internal/payments/initiate")
        PaymentInitiationResult initiatePayment(@RequestBody Map<String, String> request);
    }
}
