package com.park.ecommerce.exception;

// 여러 항목을 한 번에 검증할 때 어느 항목이 왜 잘못됐는지
public record ErrorDetail(String field, String message) {
}
