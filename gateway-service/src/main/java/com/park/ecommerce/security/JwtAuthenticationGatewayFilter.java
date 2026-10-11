package com.park.ecommerce.security;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

// 라우팅 전에 인증을 검증해 백엔드까지 요청이 도달하지 않도록 차단
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MEMBER_ID_HEADER = "X-Member-Id";
    private static final String ROLE_HEADER = "X-Role";
    private static final String VISITOR_ID_HEADER = "X-Visitor-Id";

    // 백엔드가 게이트웨이를 믿고 그대로 쓰는 식별 헤더 - 클라이언트가 보낸 값은 위조일 수 있어 항상 제거
    private static final List<String> IDENTITY_HEADERS = List.of(MEMBER_ID_HEADER, ROLE_HEADER, VISITOR_ID_HEADER);

    // 게이트웨이에 들어온 요청 중 로그인 검사를 건너뛰는 경로 목록
    private static final List<String> WHITELIST = List.of(
            "/oauth2/**", "/login/**", "/api/auth/reissue",
            "/api/products/**", "/api/categories/**", "/api/promotions/**", "/api/members/test"
    );

    // 로그인 없이 쓸 수 있지만 토큰이 있으면 검증해 회원을 식별하는 경로
    // 화이트리스트 전체로 넓히면 만료된 토큰을 들고 온 재발급 요청까지 401로 막히므로 식별이 필요한 경로만 둔다
    private static final List<String> OPTIONAL_AUTH_PATHS = List.of("/api/search/**");

    private final JwtValidator jwtValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> IDENTITY_HEADERS.forEach(headers::remove))
                .build();
        String path = request.getPath().value();

        if (matchesAny(WHITELIST, path)) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        String token = resolveToken(request);

        // 토큰이 있는데 무효하면 비회원으로 조용히 넘기지 않고 아래에서 401로 막는다
        if (token == null && matchesAny(OPTIONAL_AUTH_PATHS, path)) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        if (token == null || !jwtValidator.isValid(token)) {
            return unauthorized(exchange);
        }

        ServerHttpRequest authorizedRequest = request.mutate()
                .header(MEMBER_ID_HEADER, String.valueOf(jwtValidator.getMemberId(token)))
                .header(ROLE_HEADER, jwtValidator.getRole(token))
                .build();

        return chain.filter(exchange.mutate().request(authorizedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // 라우팅 관련 필터보다 먼저 실행
    }

    private boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");

        byte[] body = "{\"message\":\"유효하지 않은 인증 정보입니다.\"}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
