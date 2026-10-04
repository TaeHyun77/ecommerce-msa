package com.park.ecommerce.search;

import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import com.park.ecommerce.search.suggestion.ProductSuggestionRepository;
import com.park.ecommerce.search.suggestion.ProductSuggestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    private ProductSuggestionRepository productSuggestionRepository;

    @InjectMocks
    private ProductSuggestionService productSuggestionService;

    @Test
    @DisplayName("입력의 앞뒤 공백과 연속 공백을 정리해 후보를 찾고 그 결과를 돌려준다")
    void normalizesQuery() {
        given(productSuggestionRepository.suggest("고등 어")).willReturn(List.of("고등어 구이"));

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

        verify(productSuggestionRepository, never()).suggest(anyString());
    }

    @Test
    @DisplayName("입력은 50자까지 허용하고 넘으면 거부한다")
    void limitsQueryLength() {
        given(productSuggestionRepository.suggest(anyString())).willReturn(List.of());

        assertThatCode(() -> productSuggestionService.suggest("가".repeat(50))).doesNotThrowAnyException();
        assertThatThrownBy(() -> productSuggestionService.suggest("가".repeat(51)))
                .isInstanceOf(SearchException.class)
                .extracting("errorCode")
                .isEqualTo(SearchErrorCode.INVALID_SEARCH_QUERY);
    }
}
