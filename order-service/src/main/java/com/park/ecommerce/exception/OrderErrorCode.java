package com.park.ecommerce.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum OrderErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    MAX_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "한 상품은 99개까지 담을 수 있습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.BAD_REQUEST, "판매 중이 아닌 상품입니다."),
    PRODUCT_SOLD_OUT(HttpStatus.CONFLICT, "품절된 상품입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니에 담겨 있지 않은 상품입니다."),
    DUPLICATE_ORDER_PRODUCT(HttpStatus.BAD_REQUEST, "같은 상품이 여러 품목에 중복되어 있습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    PRODUCT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "상품 정보를 가져오지 못했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    OrderErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
