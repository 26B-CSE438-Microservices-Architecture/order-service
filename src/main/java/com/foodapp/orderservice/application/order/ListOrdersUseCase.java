package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.dto.response.OrderResponse;
import com.foodapp.orderservice.dto.response.PageResponse;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListOrdersUseCase {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> forCustomer(UUID userId, OrderStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var orders = status != null
                ? orderRepository.findByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findByUserId(userId, pageable);
        return PageResponse.from(orders, OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> forRestaurant(UUID restaurantId, OrderStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var orders = status != null
                ? orderRepository.findByRestaurantIdAndStatus(restaurantId, status, pageable)
                : orderRepository.findByRestaurantId(restaurantId, pageable);
        return PageResponse.from(orders, OrderResponse::from);
    }
}
