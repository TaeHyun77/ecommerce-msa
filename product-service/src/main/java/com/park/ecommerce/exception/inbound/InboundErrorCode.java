package com.park.ecommerce.exception.inbound;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum InboundErrorCode {
    DUPLICATE_LINE_PRODUCT(HttpStatus.BAD_REQUEST, "같은 상품이 여러 품목에 중복되어 있습니다."),
    INTERFACE_NOT_FOUND(HttpStatus.NOT_FOUND, "수신된 입고 예정서를 찾을 수 없습니다."),
    INTERFACE_NOT_FAILED(HttpStatus.CONFLICT, "처리에 실패한 입고 예정서만 재처리할 수 있습니다."),
    EXPECTATION_NOT_FOUND(HttpStatus.NOT_FOUND, "입고 예정 정보를 찾을 수 없습니다."),
    UNEXPECTED_RECEIPT_PRODUCT(HttpStatus.BAD_REQUEST, "입고 예정에 없는 상품이 포함되어 있습니다."),
    RECEIPT_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "양품과 불량 수량의 합이 예정 수량을 초과합니다."),
    ALREADY_RECEIVED(HttpStatus.CONFLICT, "이미 다른 입고 확정으로 처리된 입고 예정입니다.");

    private final HttpStatus status;
    private final String message;

    InboundErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
