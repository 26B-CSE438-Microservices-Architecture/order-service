package com.foodapp.orderservice.domain.entity;

import com.foodapp.orderservice.domain.valueobject.Money;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cart_id", insertable = false, updatable = false)
    private UUID cartId;

    @Column(nullable = false)
    private UUID menuItemId;

    @Column(nullable = false)
    private String menuItemName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "unit_price_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "unit_price_currency"))
    })
    private Money unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_price_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "total_price_currency"))
    })
    private Money totalPrice;

    private String specialInstructions;

    public void recalculateTotal() {
        if (unitPrice != null && quantity != null) {
            this.totalPrice = Money.of(
                    unitPrice.getAmount().multiply(java.math.BigDecimal.valueOf(quantity)),
                    unitPrice.getCurrency()
            );
        }
    }
}