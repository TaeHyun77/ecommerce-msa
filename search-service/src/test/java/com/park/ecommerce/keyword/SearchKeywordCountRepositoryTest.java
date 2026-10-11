package com.park.ecommerce.keyword;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 만료·정렬 집합 동작은 실제 Redis에서만 확인되므로 컨테이너로 검증 - 저장소만 필요해 스프링 컨텍스트 없이 직접 연결
@Testcontainers
class SearchKeywordCountRepositoryTest {
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private SearchKeywordCountRepository repository;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        repository = new SearchKeywordCountRepository(redisTemplate);
    }

    @Test
    @DisplayName("같은 식별자와 검색어 키는 기간 안에 처음 한 번만 처음 본 것으로 판정하고, 기간이 지나면 사라지도록 만료를 건다")
    void marksSeenOncePerWindow() {
        assertThat(repository.markSeen("v:visitor-1", "고등어구이", Duration.ofMinutes(30))).isTrue();
        assertThat(repository.markSeen("v:visitor-1", "고등어구이", Duration.ofMinutes(30))).isFalse();
        assertThat(repository.markSeen("v:visitor-2", "고등어구이", Duration.ofMinutes(30))).isTrue();

        assertThat(redisTemplate.getExpire("search:keyword:seen:v:visitor-1:고등어구이", TimeUnit.SECONDS))
                .isBetween(1L, 1800L);
    }

    @Test
    @DisplayName("검색어를 날짜별 집합에 표기 그대로 1씩 더하고 8일 뒤 만료되게 한다")
    void incrementsDailyCount() {
        LocalDate today = LocalDate.of(2026, 10, 11);

        repository.increment(today, "고등어 구이");
        repository.increment(today, "고등어 구이");
        repository.increment(today, "고등어구이");

        assertThat(redisTemplate.opsForZSet().score("search:keyword:count:20261011", "고등어 구이")).isEqualTo(2.0);
        assertThat(redisTemplate.opsForZSet().score("search:keyword:count:20261011", "고등어구이")).isEqualTo(1.0);
        assertThat(redisTemplate.getExpire("search:keyword:count:20261011", TimeUnit.DAYS)).isBetween(7L, 8L);
    }
}
