package com.park.ecommerce.config;

import com.park.ecommerce.exception.ProductServiceUnavailableException;
import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Configuration
public class RestClientConfig {
    @Bean
    public RestClient productServiceRestClient(
            RestClient.Builder builder,
            @Value("${external.product-service.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                // 연결 1초, 읽기 2초
                .requestFactory(requestFactory(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                .defaultStatusHandler(
                        HttpStatusCode::is5xxServerError,
                        (request, response) -> {
                            throw new ProductServiceUnavailableException();
                        }
                )
                .defaultStatusHandler(
                        HttpStatusCode::is4xxClientError,
                        (request, response) -> {
                            // 4xx는 product-service가 정상이라는 뜻이고 원인은 이쪽의 호출 계약 오류
                            // 서킷이 열리지 않아 조용히 계속 실패하므로 반드시 로그로 남기도록 함
                            log.error("product-service 호출 계약 불일치 - status: {}, uri: {}",
                                    response.getStatusCode(), request.getURI());
                            throw new SearchException(SearchErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
                        }
                )
                .build();
    }

    private ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return ClientHttpRequestFactoryBuilder.detect().build(settings);
    }
}
