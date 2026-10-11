package com.park.ecommerce.search;

import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import com.park.ecommerce.search.suggestion.KeywordSuggestionRepository;
import com.park.ecommerce.search.suggestion.ProductSuggestionRepository;
import com.park.ecommerce.search.suggestion.ProductSuggestionService;
import com.park.ecommerce.search.suggestion.ScoredSuggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductSuggestionServiceTest {
    @Mock
    private KeywordSuggestionRepository keywordSuggestionRepository;

    @Mock
    private ProductSuggestionRepository productSuggestionRepository;

    @InjectMocks
    private ProductSuggestionService productSuggestionService;

    @Test
    @DisplayName("입력의 앞뒤 공백과 연속 공백을 정리해 후보를 찾고 그 결과를 돌려준다")
    void normalizesQuery() {
        given(keywordSuggestionRepository.suggest("고등 어")).willReturn(List.of());
        given(productSuggestionRepository.suggest("고등 어")).willReturn(List.of(new ScoredSuggestion("고등어 구이", 7000f)));

        List<String> suggestions = productSuggestionService.suggest("  고등   어 ");

        assertThat(suggestions).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("공백뿐인 입력은 거부하고 조회하지 않는다")
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> productSuggestionService.suggest("   "))
                .isInstanceOf(SearchException.class)
                .extracting("errorCode")
                .isEqualTo(SearchErrorCode.INVALID_SEARCH_QUERY);

        verify(keywordSuggestionRepository, never()).suggest(anyString());
        verify(productSuggestionRepository, never()).suggest(anyString());
    }

    @Test
    @DisplayName("입력은 50자까지 허용하고 넘으면 거부한다")
    void limitsQueryLength() {
        given(keywordSuggestionRepository.suggest(anyString())).willReturn(List.of());
        given(productSuggestionRepository.suggest(anyString())).willReturn(List.of());

        assertThatCode(() -> productSuggestionService.suggest("가".repeat(50))).doesNotThrowAnyException();
        assertThatThrownBy(() -> productSuggestionService.suggest("가".repeat(51)))
                .isInstanceOf(SearchException.class)
                .extracting("errorCode")
                .isEqualTo(SearchErrorCode.INVALID_SEARCH_QUERY);
    }

    @Test
    @DisplayName("일치 단계가 높은 후보가 먼저이고, 같은 단계면 검색어가 상품명보다 먼저다")
    void mergesByStageThenKeywordFirst() {
        given(keywordSuggestionRepository.suggest("구이")).willReturn(List.of(
                new ScoredSuggestion("구이 세트", 7000f),
                new ScoredSuggestion("고등어구이", 1000f)));
        given(productSuggestionRepository.suggest("구이")).willReturn(List.of(
                new ScoredSuggestion("구이용 삼겹살", 7000f),
                new ScoredSuggestion("[바다소리] 숯불 고등어구이", 1000f)));

        assertThat(productSuggestionService.suggest("구이"))
                .containsExactly("구이 세트", "구이용 삼겹살", "고등어구이", "[바다소리] 숯불 고등어구이");
    }

    @Test
    @DisplayName("띄어쓰기만 다른 같은 이름은 앞선 후보 하나만 남긴다")
    void removesDuplicatesAcrossSources() {
        given(keywordSuggestionRepository.suggest("고등어")).willReturn(List.of(
                new ScoredSuggestion("고등어 구이", 7000f)));
        given(productSuggestionRepository.suggest("고등어")).willReturn(List.of(
                new ScoredSuggestion("고등어구이", 7000f),
                new ScoredSuggestion("고등어 조림", 7000f)));

        assertThat(productSuggestionService.suggest("고등어")).containsExactly("고등어 구이", "고등어 조림");
    }

    @Test
    @DisplayName("합친 후보는 최대 10개이고, 검색어가 부족하면 남은 칸을 상품명으로 채운다")
    void fillsWithProductNamesUpToTen() {
        given(keywordSuggestionRepository.suggest("고등어")).willReturn(IntStream.rangeClosed(1, 3)
                .mapToObj(i -> new ScoredSuggestion("검색어 " + i, 7000f)).toList());
        given(productSuggestionRepository.suggest("고등어")).willReturn(IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new ScoredSuggestion("상품 " + i, 7000f)).toList());

        assertThat(productSuggestionService.suggest("고등어")).containsExactly(
                "검색어 1", "검색어 2", "검색어 3",
                "상품 1", "상품 2", "상품 3", "상품 4", "상품 5", "상품 6", "상품 7");
    }
}
