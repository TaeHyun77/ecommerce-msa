package com.park.ecommerce.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SearchErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_SEARCH_QUERY(HttpStatus.BAD_REQUEST, "검색어는 1자 이상 50자 이하로 입력해주세요."),
    PAGE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "조회할 수 있는 페이지 범위를 넘었습니다."),
    PRODUCT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "상품 정보를 가져오지 못했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    SearchErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
