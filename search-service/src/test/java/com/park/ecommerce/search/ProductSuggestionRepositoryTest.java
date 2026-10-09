package com.park.ecommerce.search;

import com.park.ecommerce.product.ProductChangedEvent;
import com.park.ecommerce.product.ProductDocument;
import com.park.ecommerce.product.ProductDocumentRepository;
import com.park.ecommerce.search.suggestion.ProductSuggestionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

// 자모·n-gram 분석기와 쿼리는 실제 ES에서만 확인되므로 컨테이너로 검증
@Testcontainers
@DataElasticsearchTest
@Import(ProductSuggestionRepository.class)
class ProductSuggestionRepositoryTest {
    // 로컬 인프라와 같은 Dockerfile로 만든 이미지를 써서 분석기 설정까지 함께 검증
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
    private ProductSuggestionRepository productSuggestionRepository;

    @Autowired
    private ProductDocumentRepository productDocumentRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    // 문서만 지우면 삭제된 문서가 병합 전까지 단어 통계에 남아 앞 테스트의 데이터가 정렬에 섞이므로 인덱스를 새로 만듦
    @BeforeEach
    void setUp() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        indexOps.delete();
        indexOps.createWithMapping();
    }

    @Test
    @DisplayName("입력 중인 미완성 글자로도 상품명을 찾는다")
    void matchesIncompleteInput() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "유기농 우유", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고")).containsExactly("고등어 구이");
        assertThat(productSuggestionRepository.suggest("고등")).containsExactly("고등어 구이");
        assertThat(productSuggestionRepository.suggest("고등ㅇ")).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("단어의 앞부분뿐 아니라 중간에 포함된 입력도 찾는다")
    void matchesInsideWord() {
        index(1L, "간고등어", "ON_SALE");
        index(2L, "유기농 우유", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("등어")).containsExactly("간고등어");
    }

    @Test
    @DisplayName("입력한 단어가 모두 들어 있으면 어순이 달라도 찾는다")
    void matchesRegardlessOfWordOrder() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "고등어 조림", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("구이 고등")).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("띄어쓰기 없이 붙여 쓴 입력도 찾는다")
    void matchesWithoutSpaces() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "고등어 조림", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고등어구이")).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("기호로 이어진 이름도 기호 없이 입력하면 찾는다")
    void matchesNameJoinedBySymbol() {
        index(1L, "고소&아삭한 채소팩", "ON_SALE");
        index(2L, "유기농 우유", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고소아삭")).containsExactly("고소&아삭한 채소팩");
    }

    @Test
    @DisplayName("초성만 입력해도 찾는다")
    void matchesChosung() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "유기농 우유", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("ㄱㄷㅇ")).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("초성도 브랜드 뒤의 단어처럼 이름 중간에 있는 단어 시작부터 찾는다")
    void matchesChosungFromWordStart() {
        index(1L, "[바다소리] 숯불 고등어구이", "ON_SALE");
        index(2L, "간고등어", "ON_SALE"); // 단어 중간의 초성(ㄱ'ㄱㄷㅇ')은 찾지 않음
        index(3L, "유기농 우유", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("ㄱㄷㅇ")).containsExactly("[바다소리] 숯불 고등어구이");
    }

    @Test
    @DisplayName("단어가 입력으로 시작하는 상품, 마지막 글자를 치는 중인 상품, 단어 중간에 포함하는 상품 순으로 둔다")
    void ranksWordStartThenTypingThenInsideWord() {
        // 앞 단계일수록 식별자를 작게 해서, 동점 정렬(식별자 내림차순)이 아니라 단계 덕분에 앞서는지 확인
        index(1L, "고구마", "ON_SALE");
        index(2L, "곰탕", "ON_SALE"); // '고' 다음 받침 ㅁ을 치는 중일 수 있음
        index(3L, "불고기 전골", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고")).containsExactly("고구마", "곰탕", "불고기 전골");
    }

    @Test
    @DisplayName("같은 단계에서는 입력이 몇 번 나오는지와 관계없이 이름이 짧은 상품을 앞에 둔다")
    void ranksShorterNameFirstWithinSameStage() {
        // 긴 이름에 '고'로 시작하는 단어가 여러 번 나와도, 이름이 짧은 상품이 더 깔끔한 후보이므로 앞에 둔다
        index(1L, "고구마 1kg", "ON_SALE");
        index(2L, "[스위피] 오독오독 골라담기 (바삭 고구마 고구마칩)", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고")).containsExactly("고구마 1kg", "[스위피] 오독오독 골라담기 (바삭 고구마 고구마칩)");
    }

    @Test
    @DisplayName("브랜드 대괄호 뒤의 단어도 단어 시작으로 본다")
    void treatsWordAfterBrandAsWordStart() {
        index(1L, "[바다소리] 숯불 고등어구이", "ON_SALE");
        index(2L, "간고등어", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고등")).containsExactly("[바다소리] 숯불 고등어구이", "간고등어");
    }

    @Test
    @DisplayName("여러 단어를 입력하면 단어 시작부터 이어진 상품, 단어 중간부터 이어진 상품, 흩어져 있는 상품 순으로 둔다")
    void ranksMultiWordInput() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "간고등어 구이", "ON_SALE");
        index(3L, "고등어밥상 즉석구이", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고등어 구"))
                .containsExactly("고등어 구이", "간고등어 구이", "고등어밥상 즉석구이");
    }

    @Test
    @DisplayName("붙여 쓴 입력도 띄어 쓴 상품명의 단어 시작부터 이어진 것으로 본다")
    void ranksCompoundInputAsWordStart() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "간고등어구이", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고등어구")).containsExactly("고등어 구이", "간고등어구이");
    }

    @Test
    @DisplayName("판매중이 아닌 상품은 찾지 않는다")
    void suggestsOnSaleOnly() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "고등어 조림", "READY");
        index(3L, "고등어 통조림", "SUSPENDED");

        assertThat(productSuggestionRepository.suggest("고등어")).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("같은 이름의 상품은 하나만 보여준다")
    void removesDuplicateNames() {
        index(1L, "고등어 구이", "ON_SALE");
        index(2L, "고등어 구이", "ON_SALE");
        index(3L, "고등어 조림", "ON_SALE");

        assertThat(productSuggestionRepository.suggest("고등어")).containsExactlyInAnyOrder("고등어 구이", "고등어 조림");
    }

    @Test
    @DisplayName("후보는 최대 10개까지만 돌려준다")
    void limitsToTen() {
        for (long id = 1; id <= 12; id++) {
            index(id, "고등어 " + id + "호", "ON_SALE");
        }

        assertThat(productSuggestionRepository.suggest("고등어")).hasSize(10);
    }

    @Test
    @DisplayName("일치하는 상품이 없거나 색인된 상품이 하나도 없는 새 인덱스에서도 빈 목록을 돌려준다")
    void returnsEmptyWhenNothingMatches() {
        index(1L, "유기농 우유", "ON_SALE");
        assertThat(productSuggestionRepository.suggest("고등어")).isEmpty();

        // 배포 직후처럼 문서가 들어간 적 없는 인덱스 - 문서가 있으면 ES가 필드를 자동으로 매핑해 문제가 가려진다
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        indexOps.delete();
        indexOps.createWithMapping();

        assertThat(productSuggestionRepository.suggest("고등어")).isEmpty();
    }

    private void index(Long productId, String name, String status) {
        productDocumentRepository.save(ProductDocument.from(
                new ProductChangedEvent(productId, name, null, 2L, 1L, status, 10_000)
        ));
    }
}
