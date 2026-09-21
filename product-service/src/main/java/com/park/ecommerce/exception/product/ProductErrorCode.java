package com.park.ecommerce.exception.product;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ProductErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    DUPLICATE_PRODUCT_CODE(HttpStatus.CONFLICT, "이미 등록된 상품코드입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다.");

    private final HttpStatus status;
    private final String message;

    ProductErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
