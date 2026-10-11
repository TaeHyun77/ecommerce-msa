package com.park.ecommerce.keyword;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    // 오늘부터 days일치 횟수를 하루 지날 때마다 decay배 해서 표기별로 더함 - 없는 날짜의 키는 빈 집합으로 취급됨
    public Map<String, Double> sumDecayed(LocalDate today, int days, double decay) {
        List<String> keys = IntStream.range(0, days).mapToObj(day -> countKey(today.minusDays(day))).toList();
        double[] weights = IntStream.range(0, days).mapToDouble(day -> Math.pow(decay, day)).toArray();

        Set<TypedTuple<String>> counts = redisTemplate.opsForZSet()
                .unionWithScores(keys.get(0), keys.subList(1, keys.size()), Aggregate.SUM, Weights.of(weights));
        return counts.stream().collect(Collectors.toMap(TypedTuple::getValue, TypedTuple::getScore));
    }

    private static String countKey(LocalDate date) {
        return COUNT_KEY.formatted(date.format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
