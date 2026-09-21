package com.park.ecommerce.order.domain;

public enum OrderStatus {
    PAYMENT_WAITING, // 결제 대기 - 재고를 선점하고 결제를 기다리는 상태, 결제 기한이 지나면 취소
    PAID, // 결제 완료
    CANCELLED, // 취소 - 재고 부족, 결제 기한 만료, 결제 실패
    COMPLETED
}
