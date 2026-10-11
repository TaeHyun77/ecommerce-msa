package com.park.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationGatewayFilterTest {
    @Mock
    private JwtValidator jwtValidator;

    @InjectMocks
    private JwtAuthenticationGatewayFilter filter;

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @Test
    @DisplayName("상품 검색 API는 로그인하지 않아도 백엔드로 전달된다")
    void forwardsSearchWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/search/products"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwardedHeaders().getFirst("X-Member-Id")).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("상품 검색 API에 유효한 토큰이 있으면 회원 식별 헤더를 붙여 전달한다")
    void attachesMemberHeadersToSearchWithValidToken() {
        given(jwtValidator.isValid("valid-token")).willReturn(true);
        given(jwtValidator.getMemberId("valid-token")).willReturn(1L);
        given(jwtValidator.getRole("valid-token")).willReturn("USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/search/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"));

        filter.filter(exchange, chain).block();

        assertThat(forwardedHeaders().getFirst("X-Member-Id")).isEqualTo("1");
        assertThat(forwardedHeaders().getFirst("X-Role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("상품 검색 API에 유효하지 않은 토큰이 있으면 비회원으로 넘기지 않고 401로 막는다")
    void rejectsSearchWithInvalidToken() {
        given(jwtValidator.isValid("invalid-token")).willReturn(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/search/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("토큰 재발급 API는 만료된 토큰이 함께 와도 막지 않고 전달한다")
    void forwardsReissueWithExpiredToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/reissue")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("공개 API에 클라이언트가 직접 넣은 식별 헤더는 제거하고 전달한다")
    void stripsSpoofedIdentityHeadersOnPublicApi() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products/1")
                .header("X-Member-Id", "999")
                .header("X-Role", "ADMIN")
                .header("X-Visitor-Id", "spoofed"));

        filter.filter(exchange, chain).block();

        assertThat(forwardedHeaders().getFirst("X-Member-Id")).isNull();
        assertThat(forwardedHeaders().getFirst("X-Role")).isNull();
        assertThat(forwardedHeaders().getFirst("X-Visitor-Id")).isNull();
    }

    @Test
    @DisplayName("로그인이 필요한 API는 클라이언트가 넣은 식별 헤더 대신 토큰의 회원 정보로 전달한다")
    void replacesSpoofedIdentityHeadersOnProtectedApi() {
        given(jwtValidator.isValid("valid-token")).willReturn(true);
        given(jwtValidator.getMemberId("valid-token")).willReturn(1L);
        given(jwtValidator.getRole("valid-token")).willReturn("USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/ORDER-0001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header("X-Member-Id", "999")
                .header("X-Visitor-Id", "spoofed"));

        filter.filter(exchange, chain).block();

        assertThat(forwardedHeaders().get("X-Member-Id")).containsExactly("1");
        assertThat(forwardedHeaders().getFirst("X-Visitor-Id")).isNull();
    }

    @Test
    @DisplayName("로그인이 필요한 API는 토큰이 없으면 백엔드로 보내지 않고 401로 막는다")
    void rejectsProtectedApiWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/ORDER-0001"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders forwardedHeaders() {
        return forwarded.get().getRequest().getHeaders();
    }
}
