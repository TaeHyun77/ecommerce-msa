package com.park.ecommerce.order.dto;

import com.park.ecommerce.order.domain.Order;
import com.park.ecommerce.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        String orderNo,
        OrderStatus status,
        String orderName,
        Integer totalAmount,
        LocalDateTime orderedAt,
        LocalDateTime expiresAt,
        List<Line> lines
) {
    public record Line(Long productId, String productName, Integer unitPrice, Integer quantity) {
    }

    public static OrderDetailResponse from(Order order) {
        List<Line> lines = order.getLines().stream()
                .map(line -> new Line(line.getProductId(), line.getProductName(), line.getUnitPrice(), line.getQuantity()))
                .toList();
        return new OrderDetailResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getOrderName(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                order.getExpiresAt(),
                lines
        );
    }
}
