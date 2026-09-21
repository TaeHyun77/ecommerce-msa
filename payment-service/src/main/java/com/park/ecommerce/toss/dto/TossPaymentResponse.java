package com.park.ecommerce.toss.dto;

import java.time.OffsetDateTime;

// 토스페이먼츠 Payment 객체 중 승인 결과 판단에 필요한 필드만 직접 정의
// status: READY, IN_PROGRESS, DONE, ABORTED, EXPIRED 등 / approvedAt: 승인 전에는 null
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        String method,
        Integer totalAmount,
        OffsetDateTime approvedAt
) {
}
