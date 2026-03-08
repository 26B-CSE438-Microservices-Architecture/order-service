package com.foodapp.orderservice.domain.entity;

import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.domain.valueobject.Money;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = CartStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addItem(CartItem item) {
        validateActive();
        items.stream()
                .filter(i -> i.getMenuItemId().equals(item.getMenuItemId()))
                .findFirst()
                .ifPresentOrElse(
                        existing -> {
                            existing.setQuantity(existing.getQuantity() + item.getQuantity());
                            existing.recalculateTotal();
                        },
                        () -> items.add(item)
                );
    }

    public void removeItem(UUID itemId) {
        validateActive();
        items.removeIf(i -> i.getId().equals(itemId));
    }

    public void updateItemQuantity(UUID itemId, int quantity) {
        validateActive();
        items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .ifPresent(item -> {
                    item.setQuantity(quantity);
                    item.recalculateTotal();
                });
    }

    public void clear() {
        validateActive();
        items.clear();
    }

    public void checkout() {
        validateActive();
        if (items.isEmpty()) throw new IllegalStateException("Cannot checkout an empty cart");
        this.status = CartStatus.CHECKED_OUT;
    }

    public Money getTotalAmount() {
        if (items.isEmpty()) return Money.zero("TRY");
        String currency = items.get(0).getUnitPrice().getCurrency();
        return items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(Money::add)
                .orElse(Money.zero(currency));
    }

    private void validateActive() {
        if (status != CartStatus.ACTIVE) {
            throw new IllegalStateException("Cart is not active. Current status: " + status);
        }
    }
}
