package com.park.ecommerce.exception.category;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CategoryErrorCode {
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    INVALID_PARENT_CATEGORY(HttpStatus.BAD_REQUEST, "하위 카테고리 아래에는 카테고리를 만들 수 없습니다."),
    NOT_SUB_CATEGORY(HttpStatus.BAD_REQUEST, "상품은 하위 카테고리에만 등록할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    CategoryErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
