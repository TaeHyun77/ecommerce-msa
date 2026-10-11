package com.park.ecommerce.keyword;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// 검색어 검색 횟수를 Redis에 날짜별로 기록 - ES 검색어 인덱스는 이 횟수로 주기마다 다시 계산됨
@Repository
@RequiredArgsConstructor
public class SearchKeywordCountRepository {
    private static final String SEEN_KEY = "search:keyword:seen:%s:%s"; // 식별자, 검색어 키
    private static final String COUNT_KEY = "search:keyword:count:%s"; // yyyyMMdd
    private static final Duration COUNT_TTL = Duration.ofDays(8); // 집계 기간 7일 + 날짜가 바뀌는 시점의 여유 하루

    private final StringRedisTemplate redisTemplate;

    // 같은 사람의 같은 검색어를 window 동안 한 번만 세기 위해, 처음 본 조합인지 확인하면서 표시를 남김
    public boolean markSeen(String searcherId, String keywordKey, Duration window) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(SEEN_KEY.formatted(searcherId, keywordKey), "1", window));
    }

    public void increment(LocalDate date, String keyword) {
        String key = countKey(date);
        redisTemplate.opsForZSet().incrementScore(key, keyword, 1);
        redisTemplate.expire(key, COUNT_TTL);
    }

    private static String countKey(LocalDate date) {
        return COUNT_KEY.formatted(date.format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
