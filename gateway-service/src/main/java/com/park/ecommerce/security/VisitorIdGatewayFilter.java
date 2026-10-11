package com.park.ecommerce.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

// 비회원 검색 사용자를 구분하도록 vid 쿠키를 발급하고, 돌려받은 쿠키 값을 X-Visitor-Id 헤더로 전달
// 쿠키는 클라이언트가 지우거나 바꿀 수 있어 위조를 막지 못한다 - 형식만 검증하고, 새로 발급한 요청에는 헤더를 싣지 않아
// 쿠키를 저장하지 않는 클라이언트(curl, 봇)가 매번 새 id로 중복 제거를 우회하지 못하게 함
@Component
public class VisitorIdGatewayFilter implements GlobalFilter, Ordered {
    private static final String VISITOR_ID_COOKIE = "vid";
    private static final String VISITOR_ID_HEADER = "X-Visitor-Id";
    private static final Duration VISITOR_ID_MAX_AGE = Duration.ofDays(365);
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    // 비회원 식별이 필요한 경로 - 지금은 검색어 집계만 사용
    private static final List<String> TARGET_PATHS = List.of("/api/search/**");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (TARGET_PATHS.stream().noneMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }

        String visitorId = resolveVisitorId(exchange.getRequest());

        // 브라우저는 검색 전에 자동완성 요청에서 쿠키를 먼저 받으므로 첫 요청을 식별하지 않아도 놓치는 검색이 거의 없다
        if (visitorId == null) {
            exchange.getResponse().addCookie(ResponseCookie.from(VISITOR_ID_COOKIE, UUID.randomUUID().toString())
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(VISITOR_ID_MAX_AGE)
                    .build());
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(VISITOR_ID_HEADER, visitorId)
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    // JwtAuthenticationGatewayFilter가 외부 식별 헤더를 지운 뒤에 붙여야 하므로 그보다 늦게 실행
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    // UUID 형식이 아닌 값은 집계 키로 쓰지 않도록 버리고 새로 발급
    private String resolveVisitorId(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(VISITOR_ID_COOKIE);
        if (cookie == null || !UUID_PATTERN.matcher(cookie.getValue()).matches()) {
            return null;
        }
        return cookie.getValue();
    }
}
