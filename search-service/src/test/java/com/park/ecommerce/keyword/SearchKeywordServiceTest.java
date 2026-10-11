package com.park.ecommerce.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SearchKeywordServiceTest {
    private static final String SEARCHER = "v:visitor-1";
    private static final Duration DEDUPE_WINDOW = Duration.ofMinutes(30);

    @Mock
    private SearchKeywordCountRepository searchKeywordCountRepository;

    @InjectMocks
    private SearchKeywordService searchKeywordService;

    @Test
    @DisplayName("처음 검색한 검색어는 오늘 날짜에 표기 그대로 1 더하고, 중복 판정은 띄어쓰기를 뺀 키로 한다")
    void countsFirstSearch() {
        given(searchKeywordCountRepository.markSeen(SEARCHER, "고등어구이", DEDUPE_WINDOW)).willReturn(true);

        searchKeywordService.record("고등어 구이", SearchSource.TYPED, SEARCHER);

        verify(searchKeywordCountRepository).increment(LocalDate.now(), "고등어 구이");
    }

    @Test
    @DisplayName("같은 사람이 30분 안에 같은 검색어를 다시 검색하면 세지 않는다")
    void skipsRepeatedSearch() {
        given(searchKeywordCountRepository.markSeen(SEARCHER, "고등어구이", DEDUPE_WINDOW)).willReturn(false);

        searchKeywordService.record("고등어구이", SearchSource.TYPED, SEARCHER);

        verify(searchKeywordCountRepository, never()).increment(any(), anyString());
    }

    @Test
    @DisplayName("검색한 사람을 알 수 없거나 기호만 있는 검색어는 Redis를 거치지 않고 건너뛴다")
    void skipsUnidentifiedOrSymbolOnly() {
        searchKeywordService.record("고등어", SearchSource.TYPED, null);
        searchKeywordService.record("!!!", SearchSource.TYPED, SEARCHER);

        verifyNoInteractions(searchKeywordCountRepository);
    }

    @Test
    @DisplayName("자동완성 후보를 눌러 시작한 검색은 검색어 인덱스가 생기기 전에는 세지 않는다")
    void skipsSuggestionClick() {
        searchKeywordService.record("고등어", SearchSource.SUGGESTION, SEARCHER);

        verifyNoInteractions(searchKeywordCountRepository);
    }

    @Test
    @DisplayName("Redis에 기록하지 못해도 예외를 밖으로 던지지 않는다")
    void swallowsRedisFailure() {
        given(searchKeywordCountRepository.markSeen(anyString(), anyString(), any()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        assertThatCode(() -> searchKeywordService.record("고등어", SearchSource.TYPED, SEARCHER))
                .doesNotThrowAnyException();
    }
}
