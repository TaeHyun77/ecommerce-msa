package com.park.ecommerce.toss;

import com.park.ecommerce.toss.dto.TossConfirmRequest;
import com.park.ecommerce.toss.dto.TossErrorResponse;
import com.park.ecommerce.toss.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

// 토스페이먼츠 결제 승인·조회 API 호출 - 성공 응답이 아니면 모두 TossPaymentsException으로 바꿔 호출하는 쪽이 한 가지 방식으로 처리하도록 함
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient tossPaymentsRestClient;

    // 주문번호를 멱등키로 보냄 - 결과를 몰라 같은 주문을 다시 승인해도 중복 승인되지 않고 첫 요청의 결과가 돌아옴
    public TossPaymentResponse confirm(String paymentKey, String orderId, int amount) {
        return call(() -> tossPaymentsRestClient.post()
                .uri("/v1/payments/confirm")
                .header(IDEMPOTENCY_KEY_HEADER, orderId)
                .body(new TossConfirmRequest(paymentKey, orderId, amount))
                .retrieve()
                .body(TossPaymentResponse.class));
    }

    // 결제 결과를 조회
    public TossPaymentResponse find(String paymentKey) {
        return call(() -> tossPaymentsRestClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse.class));
    }

    // 승인/조회 요청을 실제로 실행하고, 성공하지 못한 경우를 모두 TossPaymentsException으로 바꿔줌
    private TossPaymentResponse call(Supplier<TossPaymentResponse> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            TossErrorResponse error = e.getResponseBodyAs(TossErrorResponse.class);
            if (error == null) {
                throw new TossPaymentsException(null, "토스페이먼츠 오류 응답 status=" + e.getStatusCode().value());
            }
            throw new TossPaymentsException(error.code(), error.message());
        } catch (ResourceAccessException e) {
            // 타임아웃, 연결 실패 - 요청이 토스에 도달해 처리됐는지 알 수 없다
            throw new TossPaymentsException(null, "토스페이먼츠 호출 실패: " + e.getMessage());
        }
    }
}
