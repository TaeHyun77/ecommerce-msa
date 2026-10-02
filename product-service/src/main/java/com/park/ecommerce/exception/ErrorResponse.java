package com.park.ecommerce.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.park.ecommerce.exception.category.CategoryErrorCode;
import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.reservation.ReservationErrorCode;
import lombok.Getter;

import java.util.List;

@Getter
public class ErrorResponse {
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_EMPTY) // 항목별 오류가 없는 응답은 기존 형식(code, message)을 그대로 유지
    private final List<ErrorDetail> errors;

    private ErrorResponse(String code, String message, List<ErrorDetail> errors) {
        this.code = code;
        this.message = message;
        this.errors = errors;
    }

    public static ErrorResponse from(ProductErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse from(CategoryErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse from(InboundErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse from(ReservationErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    // 어떤 필드가 잘못됐는지 알려주기 위해 ErrorCode의 기본 메시지 대신 검증 메시지를 사용
    public static ErrorResponse of(ProductErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, List.of());
    }

    // 여러 항목을 한 번에 검증한 경우(예: 상품 일괄 등록) 항목별 사유를 함께 전달
    public static ErrorResponse of(ProductErrorCode errorCode, String message, List<ErrorDetail> errors) {
        return new ErrorResponse(errorCode.name(), message, errors);
    }
}
