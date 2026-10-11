package com.park.ecommerce.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    @Mock
    private SearchKeywordDocumentRepository searchKeywordDocumentRepository;

    @InjectMocks
    private SearchKeywordService searchKeywordService;

    @Captor
    private ArgumentCaptor<List<SearchKeywordDocument>> documentsCaptor;

    @Captor
    private ArgumentCaptor<Long> aggregatedAtCaptor;

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
    @DisplayName("자동완성 후보를 눌러 시작한 검색은 검색어 인덱스에 있는 검색어면 띄어쓰기가 달라도 센다")
    void countsSuggestionClickOfIndexedKeyword() {
        given(searchKeywordDocumentRepository.existsById("고등어구이")).willReturn(true);
        given(searchKeywordCountRepository.markSeen(SEARCHER, "고등어구이", DEDUPE_WINDOW)).willReturn(true);

        searchKeywordService.record("고등어구이", SearchSource.SUGGESTION, SEARCHER);

        verify(searchKeywordCountRepository).increment(LocalDate.now(), "고등어구이");
    }

    @Test
    @DisplayName("자동완성 후보를 눌러 시작한 검색이 검색어 인덱스에 없으면 상품명 후보 클릭으로 보고 세지 않는다")
    void skipsSuggestionClickOfProductName() {
        given(searchKeywordDocumentRepository.existsById("바다소리숯불고등어구이70g")).willReturn(false);

        searchKeywordService.record("[바다소리] 숯불 고등어구이 70g", SearchSource.SUGGESTION, SEARCHER);

        verifyNoInteractions(searchKeywordCountRepository);
    }

    @Test
    @DisplayName("검색어 인덱스를 조회하지 못해도 예외를 밖으로 던지지 않는다")
    void swallowsIndexFailure() {
        given(searchKeywordDocumentRepository.existsById(anyString()))
                .willThrow(new DataAccessResourceFailureException("ES 연결 실패"));

        assertThatCode(() -> searchKeywordService.record("고등어", SearchSource.SUGGESTION, SEARCHER))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis에 기록하지 못해도 예외를 밖으로 던지지 않는다")
    void swallowsRedisFailure() {
        given(searchKeywordCountRepository.markSeen(anyString(), anyString(), any()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        assertThatCode(() -> searchKeywordService.record("고등어", SearchSource.TYPED, SEARCHER))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("띄어쓰기만 다른 표기는 횟수를 합쳐 한 검색어로 만들고, 가장 많이 검색된 표기를 보여준다")
    void mergesSpacingVariants() {
        given(searchKeywordCountRepository.sumDecayed(LocalDate.now(), 7, 0.9)).willReturn(Map.of(
                "고등어 구이", 2.0,
                "고등어구이", 1.5,
                "순살 고등어", 3.0));

        int indexed = searchKeywordService.aggregate();

        assertThat(indexed).isEqualTo(2);
        verify(searchKeywordDocumentRepository).saveAll(documentsCaptor.capture());
        assertThat(documentsCaptor.getValue())
                .extracting(SearchKeywordDocument::getId, SearchKeywordDocument::getName, SearchKeywordDocument::getPopularity)
                .containsExactlyInAnyOrder(
                        tuple("고등어구이", "고등어 구이", 3.5),
                        tuple("순살고등어", "순살 고등어", 3.0));
    }

    @Test
    @DisplayName("횟수가 같은 표기끼리는 사전순으로 앞선 표기를 보여줘 회차마다 바뀌지 않게 한다")
    void picksDisplayDeterministically() {
        given(searchKeywordCountRepository.sumDecayed(LocalDate.now(), 7, 0.9)).willReturn(Map.of(
                "고등어구이", 2.0,
                "고등어 구이", 2.0));

        searchKeywordService.aggregate();

        verify(searchKeywordDocumentRepository).saveAll(documentsCaptor.capture());
        assertThat(documentsCaptor.getValue()).extracting(SearchKeywordDocument::getName).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("합친 횟수가 3 미만인 검색어는 색인하지 않는다")
    void skipsBelowMinimum() {
        given(searchKeywordCountRepository.sumDecayed(LocalDate.now(), 7, 0.9)).willReturn(Map.of(
                "고등어 구이", 2.9,
                "순살 고등어", 3.0));

        searchKeywordService.aggregate();

        verify(searchKeywordDocumentRepository).saveAll(documentsCaptor.capture());
        assertThat(documentsCaptor.getValue()).extracting(SearchKeywordDocument::getName).containsExactly("순살 고등어");
    }

    @Test
    @DisplayName("이번 회차에 쓴 문서와 같은 시각을 기준으로 그보다 먼저 쓴 문서를 지운다")
    void deletesStaleWithSameAggregatedAt() {
        given(searchKeywordCountRepository.sumDecayed(LocalDate.now(), 7, 0.9)).willReturn(Map.of("순살 고등어", 3.0));

        searchKeywordService.aggregate();

        verify(searchKeywordDocumentRepository).saveAll(documentsCaptor.capture());
        verify(searchKeywordDocumentRepository).deleteByAggregatedAtLessThan(aggregatedAtCaptor.capture());
        assertThat(documentsCaptor.getValue().get(0).getAggregatedAt()).isEqualTo(aggregatedAtCaptor.getValue());
    }

    @Test
    @DisplayName("색인할 검색어가 없어도 이전 회차 문서는 지운다")
    void deletesEvenWhenNothingToIndex() {
        given(searchKeywordCountRepository.sumDecayed(LocalDate.now(), 7, 0.9)).willReturn(Map.of());

        assertThat(searchKeywordService.aggregate()).isZero();

        verify(searchKeywordDocumentRepository, never()).saveAll(any());
        verify(searchKeywordDocumentRepository).deleteByAggregatedAtLessThan(anyLong());
    }

    @Test
    @DisplayName("Redis에서 횟수를 읽지 못하면 기존 문서를 지우지 않는다")
    void keepsIndexWhenRedisFails() {
        given(searchKeywordCountRepository.sumDecayed(LocalDate.now(), 7, 0.9))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        assertThatThrownBy(() -> searchKeywordService.aggregate()).isInstanceOf(RedisConnectionFailureException.class);

        verifyNoInteractions(searchKeywordDocumentRepository);
    }
}
