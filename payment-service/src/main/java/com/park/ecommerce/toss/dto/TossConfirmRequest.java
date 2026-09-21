package com.park.ecommerce.toss.dto;

// 토스페이먼츠 결제 승인 API 요청 - 필드 이름이 곧 토스 API 규격
public record TossConfirmRequest(String paymentKey, String orderId, Integer amount) {
}
