package com.park.ecommerce.inbound;

public enum InboundStatus {
    COMPLETED, // 모든 품목이 재고에 반영됨
    INCOMPLETE // 반영하지 못한 품목이 남아 있음 - 같은 예정서 번호로 다시 보내면 남은 품목만 처리
}
