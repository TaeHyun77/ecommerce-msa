package com.park.ecommerce.exception.inbound;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum InboundErrorCode {
    DUPLICATE_LINE_PRODUCT(HttpStatus.BAD_REQUEST, "같은 상품이 여러 품목에 중복되어 있습니다.");

    private final HttpStatus status;
    private final String message;

    InboundErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
