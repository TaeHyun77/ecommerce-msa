package com.park.ecommerce.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {
    private final String code;
    private final String message;

    private ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ErrorResponse from(SearchErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    // 어떤 파라미터가 잘못됐는지 알려주기 위해 ErrorCode의 기본 메시지 대신 검증 메시지를 사용
    public static ErrorResponse of(SearchErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
