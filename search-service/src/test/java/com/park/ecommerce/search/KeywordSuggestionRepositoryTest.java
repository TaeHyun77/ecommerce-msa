package com.park.ecommerce.search;

import com.park.ecommerce.keyword.SearchKeywordDocument;
import com.park.ecommerce.keyword.SearchKeywordDocumentRepository;
import com.park.ecommerce.product.ProductChangedEvent;
import com.park.ecommerce.product.ProductDocument;
import com.park.ecommerce.product.ProductDocumentRepository;
import com.park.ecommerce.search.suggestion.KeywordSuggestionRepository;
import com.park.ecommerce.search.suggestion.ProductSuggestionRepository;
import com.park.ecommerce.search.suggestion.ScoredSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 분석기·단계 점수가 상품명 인덱스와 같게 동작하는지는 실제 ES에서만 확인되므로 컨테이너로 검증
@Testcontainers
@DataElasticsearchTest
@Import({KeywordSuggestionRepository.class, ProductSuggestionRepository.class})
class KeywordSuggestionRepositoryTest {
    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse(new ImageFromDockerfile("ecommerce-elasticsearch-nori", false)
                            .withDockerfile(Path.of("../docker/elasticsearch/Dockerfile"))
                            .get())
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private KeywordSuggestionRepository keywordSuggestionRepository;

    @Autowired
    private ProductSuggestionRepository productSuggestionRepository;

    @Autowired
    private SearchKeywordDocumentRepository searchKeywordDocumentRepository;

    @Autowired
    private ProductDocumentRepository productDocumentRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @BeforeEach
    void setUp() {
        for (Class<?> documentClass : List.of(SearchKeywordDocument.class, ProductDocument.class)) {
            IndexOperations indexOps = elasticsearchOperations.indexOps(documentClass);
            indexOps.delete();
            indexOps.createWithMapping();
        }
    }

    @Test
    @DisplayName("상품명과 같은 규칙으로 미완성 글자, 붙여 쓴 입력, 초성 입력에 일치하는 검색어를 찾는다")
    void matchesLikeProductNames() {
        keyword("고등어 구이", 5);
        keyword("유기농 우유", 5);

        assertThat(texts("고등ㅇ")).containsExactly("고등어 구이");
        assertThat(texts("고등어구이")).containsExactly("고등어 구이");
        assertThat(texts("ㄱㄷㅇ")).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("일치 단계가 먼저이고, 같은 단계 안에서는 많이 검색된 검색어, 그다음 짧은 검색어를 앞에 둔다")
    void ranksStageThenPopularityThenLength() {
        keyword("불고기", 100); // 단어 중간 일치 - 가장 많이 검색됐어도 뒤
        keyword("고등어 구이", 10);
        keyword("고구마", 10);
        keyword("고추장", 20);

        assertThat(texts("고")).containsExactly("고추장", "고구마", "고등어 구이", "불고기");
    }

    @Test
    @DisplayName("같은 입력에 같은 이름이면 상품명 후보와 같은 점수를 받아 결과를 그대로 합칠 수 있다")
    void scoresSameAsProductNames() {
        keyword("고등어 구이", 5);
        keyword("간고등어", 5);
        product(1L, "고등어 구이");
        product(2L, "간고등어");

        for (String input : List.of("고", "고등", "등어", "ㄱㄷㅇ", "고등어 구")) {
            assertThat(keywordSuggestionRepository.suggest(input))
                    .as(input)
                    .containsExactlyInAnyOrderElementsOf(productSuggestionRepository.suggest(input));
        }
    }

    @Test
    @DisplayName("색인된 검색어가 없으면 빈 목록을 돌려준다")
    void returnsEmptyWhenNoKeywords() {
        assertThat(keywordSuggestionRepository.suggest("고등어")).isEmpty();
    }

    private void keyword(String name, double popularity) {
        searchKeywordDocumentRepository.save(SearchKeywordDocument.of(name, popularity, 1L));
    }

    private void product(Long productId, String name) {
        productDocumentRepository.save(ProductDocument.from(
                new ProductChangedEvent(productId, name, null, 2L, 1L, "ON_SALE", 10_000)
        ));
    }

    private List<String> texts(String input) {
        return keywordSuggestionRepository.suggest(input).stream().map(ScoredSuggestion::text).toList();
    }
}
