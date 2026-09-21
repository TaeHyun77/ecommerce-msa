package com.park.ecommerce.reservation.domain;

public enum StockReservationStatus {
    RESERVED, // 선점 - 재고를 차감한 상태, 만료 시각이 지나면 해제 대상
    CONFIRMED, // 확정 - 결제 승인 직전에 전환되며 만료 대상에서 제외
    RELEASED // 해제 - 만료, 결제 실패 등으로 재고를 복구한 상태
}
