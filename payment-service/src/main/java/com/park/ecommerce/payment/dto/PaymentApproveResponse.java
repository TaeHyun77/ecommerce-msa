package com.park.ecommerce.payment.dto;

import com.park.ecommerce.payment.domain.Payment;
import com.park.ecommerce.payment.domain.PaymentStatus;

// 결과가 확정된 승인만 응답 (DONE, FAILED) - 결과를 모르면 응답 대신 503으로 알림
public record PaymentApproveResponse(String orderNo, PaymentStatus status, String failReason) {
    public static PaymentApproveResponse from(Payment payment) {
        return new PaymentApproveResponse(payment.getOrderNo(), payment.getStatus(), payment.getFailReason());
    }
}
