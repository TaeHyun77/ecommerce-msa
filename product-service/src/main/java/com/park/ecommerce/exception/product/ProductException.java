package com.park.ecommerce.exception.product;

import lombok.Getter;

@Getter
public class ProductException extends RuntimeException {
    private final ProductErrorCode errorCode;

    public ProductException(ProductErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
