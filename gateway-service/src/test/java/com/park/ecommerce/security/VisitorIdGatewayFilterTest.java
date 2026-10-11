package com.park.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorIdGatewayFilterTest {
    private static final String VISITOR_ID = "550e8400-e29b-41d4-a716-446655440000";

    private final VisitorIdGatewayFilter filter = new VisitorIdGatewayFilter();

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @Test
    @DisplayName("검색 요청에 vid 쿠키가 없으면 새로 발급하고, 이번 요청에는 X-Visitor-Id를 싣지 않는다")
    void issuesVisitorIdWithoutForwardingWhenCookieMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/search/products"));

        filter.filter(exchange, chain).block();

        ResponseCookie issued = exchange.getResponse().getCookies().getFirst("vid");
        assertThat(issued).isNotNull();
        assertThat(issued.isHttpOnly()).isTrue();
        assertThat(issued.isSecure()).isTrue();
        assertThat(issued.getSameSite()).isEqualTo("Lax");
        assertThat(issued.getMaxAge()).isEqualTo(Duration.ofDays(365));
        assertThat(forwardedVisitorId()).isNull();
    }

    @Test
    @DisplayName("검색 요청에 유효한 vid 쿠키가 있으면 다시 발급하지 않고 그 값을 전달한다")
    void reusesValidVisitorId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/search/products")
                .cookie(new HttpCookie("vid", VISITOR_ID)));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getCookies().getFirst("vid")).isNull();
        assertThat(forwardedVisitorId()).isEqualTo(VISITOR_ID);
    }

    @Test
    @DisplayName("vid 쿠키가 UUID 형식이 아니면 새로 발급하고, 이번 요청에는 X-Visitor-Id를 싣지 않는다")
    void reissuesMalformedVisitorIdWithoutForwarding() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/search/products")
                .cookie(new HttpCookie("vid", "not-a-uuid")));

        filter.filter(exchange, chain).block();

        ResponseCookie issued = exchange.getResponse().getCookies().getFirst("vid");
        assertThat(issued).isNotNull();
        assertThat(issued.getValue()).isNotEqualTo("not-a-uuid");
        assertThat(forwardedVisitorId()).isNull();
    }

    @Test
    @DisplayName("검색 외 경로는 vid 쿠키를 발급하지 않고 X-Visitor-Id도 붙이지 않는다")
    void skipsNonSearchPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products/1"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getCookies().getFirst("vid")).isNull();
        assertThat(forwardedVisitorId()).isNull();
    }

    private String forwardedVisitorId() {
        return forwarded.get().getRequest().getHeaders().getFirst("X-Visitor-Id");
    }
}
