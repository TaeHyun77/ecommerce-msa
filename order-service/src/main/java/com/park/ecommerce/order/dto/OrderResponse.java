package com.park.ecommerce.order.dto;

import com.park.ecommerce.order.domain.Order;
import com.park.ecommerce.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        Long memberId,
        Integer totalAmount,
        OrderStatus status,
        LocalDateTime orderedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getMemberId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getOrderedAt()
        );
    }
}
