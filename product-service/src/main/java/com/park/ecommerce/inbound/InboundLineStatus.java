package com.park.ecommerce.inbound;

public enum InboundLineStatus {
    PENDING, // 아직 처리하지 않음
    RECEIVED, // 재고에 반영됨 - 같은 예정서가 다시 와도 다시 반영하지 않음
    FAILED // 반영하지 못함 - 사유는 failReason에 남기고 후처리(재전송)로 다시 시도
}
