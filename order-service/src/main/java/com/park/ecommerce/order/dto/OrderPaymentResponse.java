package com.park.ecommerce.order.dto;

import com.park.ecommerce.order.domain.OrderStatus;

// status: PAID(결제 완료) 또는 APPROVING(결과 확인 중 - 주문 조회로 최종 결과를 확인)
public record OrderPaymentResponse(String orderNo, OrderStatus status) {
}
