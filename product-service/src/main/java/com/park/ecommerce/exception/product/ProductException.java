package com.park.ecommerce.exception.product;

import com.park.ecommerce.exception.ErrorDetail;
import lombok.Getter;

import java.util.List;

@Getter
public class ProductException extends RuntimeException {
    private final ProductErrorCode errorCode;
    private final List<ErrorDetail> errors; // 일괄 등록처럼 여러 항목을 검증할 때만 채워짐

    public ProductException(ProductErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public ProductException(ProductErrorCode errorCode, List<ErrorDetail> errors) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.errors = errors;
    }
}
