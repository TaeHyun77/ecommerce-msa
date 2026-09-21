package com.park.ecommerce.product;

import com.park.ecommerce.exception.OrderErrorCode;
import com.park.ecommerce.exception.OrderException;
import com.park.ecommerce.exception.ProductServiceUnavailableException;
import com.park.ecommerce.product.dto.ProductSummaryResponse;
import com.park.ecommerce.product.dto.StockReservationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductApiClient {
    private final RestClient productServiceRestClient;

    /** 등록되지 않은 상품 식별자는 응답에서 빠지므로, 호출하는 쪽에서 누락 여부로 미등록을 판단 - 상품 정보 조회
     * Circuit이 OPEN이면 실제 호출 없이 CallNotPermittedException 발생
     * → GlobalExceptionHandler에서 HTTP 503으로 변환
     */
    @CircuitBreaker(name = "productService")
    public List<ProductSummaryResponse> findProducts(Collection<Long> productIds) {
        try {
            return productServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/products")
                            .queryParam("ids", productIds)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ProductSummaryResponse>>() {});
        } catch (ResourceAccessException e) {
            // 타임아웃, 커넥션 거부 등 HTTP 상태 코드 이전 단계의 통신 실패 - defaultStatusHandler로는 잡히지 않음
            throw new ProductServiceUnavailableException();
        }
    }

    // 한 상품이라도 재고가 부족하면 product-service가 전체를 롤백하고 409로 응답한다
    @CircuitBreaker(name = "productService")
    public void reserve(StockReservationRequest request) {
        try {
            productServiceRestClient.post()
                    .uri("/internal/stock-reservations")
                    .body(request)
                    .retrieve()
                    // 새 주문번호로만 호출하므로 409는 재고 부족을 뜻한다 - 호출별 처리가 공통 4xx 처리(계약 불일치)보다 먼저 적용됨
                    .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(), (req, res) -> {
                        throw new OrderException(OrderErrorCode.INSUFFICIENT_STOCK);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new ProductServiceUnavailableException();
        }
    }
}
