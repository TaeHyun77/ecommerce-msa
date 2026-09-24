package com.park.ecommerce.exception.category;

import lombok.Getter;

@Getter
public class CategoryException extends RuntimeException {
    private final CategoryErrorCode errorCode;

    public CategoryException(CategoryErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
