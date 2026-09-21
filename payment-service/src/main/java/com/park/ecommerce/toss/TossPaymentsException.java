package com.park.ecommerce.toss;

import lombok.Getter;

// 토스페이먼츠 호출이 성공 응답을 받지 못한 경우 - 오류 응답, 타임아웃, 연결 실패
@Getter
public class TossPaymentsException extends RuntimeException {
    private final String code; // 토스 에러 코드 - 응답을 받지 못한 경우 null

    public TossPaymentsException(String code, String message) {
        super(message);
        this.code = code;
    }
}
