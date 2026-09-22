package com.park.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 토스페이먼츠 결제창 인증이 끝나 successUrl로 전달된 값 - 브라우저를 거쳐 오므로 금액은 주문 금액과 대조한 뒤 사용
public record OrderPaymentRequest(
        @NotBlank(message = "결제 키는 필수입니다.")
        String paymentKey,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Positive(message = "결제 금액은 0원보다 커야 합니다.")
        Integer amount
) {
}
