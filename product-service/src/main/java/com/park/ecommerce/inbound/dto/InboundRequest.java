package com.park.ecommerce.inbound.dto;

import com.park.ecommerce.inbound.Inbound;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// 공급사가 보내는 입고 예정서
public record InboundRequest(
        @NotBlank(message = "입고 예정서 번호는 필수입니다.")
        String asnNo,

        @NotBlank(message = "공급사 코드는 필수입니다.")
        String supplierCode,

        @NotEmpty(message = "입고 품목은 1개 이상이어야 합니다.")
        List<@Valid Line> lines
) {
    public record Line(
            @NotBlank(message = "상품코드는 필수입니다.")
            String productCode,

            @NotNull(message = "예정 수량은 필수입니다.")
            @Positive(message = "예정 수량은 1개 이상이어야 합니다.")
            Integer quantity
    ) {
    }

    public Inbound toEntity() {
        Inbound inbound = Inbound.builder()
                .asnNo(asnNo)
                .supplierCode(supplierCode)
                .build();
        lines.forEach(line -> inbound.addLine(line.productCode(), line.quantity()));
        return inbound;
    }

    // 상품 코드 리스트 반환
    private List<String> productCodes() {
        return lines.stream().map(Line::productCode).toList();
    }

    // 중복된 상품 없는지 확인
    public boolean hasDuplicateProduct() {
        return productCodes().stream().distinct().count() != lines.size();
    }
}
