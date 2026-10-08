package com.park.ecommerce.search;

import com.park.ecommerce.product.ProductChangedEvent;
import com.park.ecommerce.product.ProductDocument;
import com.park.ecommerce.product.ProductDocumentRepository;
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

// 형태소 매칭, 필터, 정렬은 nori가 설치된 실제 ES에서만 확인되므로 컨테이너로 검증
@Testcontainers
@DataElasticsearchTest
@Import(ProductSearchRepository.class)
class ProductSearchRepositoryTest {
    // 로컬 인프라와 같은 Dockerfile로 nori가 설치된 이미지를 만들어 분석기 설정까지 함께 검증
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
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ProductDocumentRepository productDocumentRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    // 문서만 지우면 삭제된 문서가 병합 전까지 단어 통계(IDF)에 남아 앞 테스트의 데이터가 정렬에 섞이므로 인덱스를 새로 만듦
    @BeforeEach
    void setUp() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        indexOps.delete();
        indexOps.createWithMapping();
    }

    @Test
    @DisplayName("검색어의 단어 중 하나만 포함해도 찾고, 모두 포함한 상품을 앞에 둔다")
    void matchesAnyTokenAndRanksFullMatchFirst() {
        index(1L, "제주 삼겹살 구이", "ON_SALE");
        index(2L, "노르웨이 고등어 구이", "ON_SALE");
        index(3L, "순살 고등어", "ON_SALE");
        index(4L, "유기농 우유", "ON_SALE");

        ProductSearchHits hits = productSearchRepository.search("고등어 구이", 0, 20);

        assertThat(hits.productIds().get(0)).isEqualTo(2L);
        assertThat(hits.productIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(hits.totalHits()).isEqualTo(3);
    }

    @Test
    @DisplayName("문구가 그대로 일치하는 상품, 모든 단어를 포함한 상품, 일부만 포함한 상품 순으로 둔다")
    void ranksPhraseThenAllTermsThenPartial() {
        // 앞 단계일수록 식별자를 작게 해서, 동점 정렬(식별자 내림차순)이 아니라 단계 덕분에 앞서는지 확인
        index(1L, "노르웨이 고등어 구이", "ON_SALE");
        index(2L, "고등어 소금 구이", "ON_SALE");
        index(3L, "순살 고등어", "ON_SALE");

        assertThat(productSearchRepository.search("고등어 구이", 0, 20).productIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("붙여 쓴 상품명도 검색어와 문구가 일치하는 것으로 본다")
    void ranksCompoundNameAsPhraseMatch() {
        // nori가 "고등어구이"를 고등어, 구이 연속 토큰으로 나누므로 문구 일치 단계에 든다
        index(1L, "숯불 고등어구이", "ON_SALE");
        index(2L, "고등어 소금 구이", "ON_SALE");

        assertThat(productSearchRepository.search("고등어 구이", 0, 20).productIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("일부 단어만 일치하면 이름 길이와 관계없이 더 희소한 단어를 포함한 상품을 앞에 둔다")
    void ranksRarerTermFirstRegardlessOfNameLength() {
        // 길이 보정이 켜져 있으면 짧은 '구이' 상품이 긴 '고등어' 상품을 앞선다
        index(1L, "노르웨이 대서양 손질 냉동 고등어 필렛 특가 상품", "ON_SALE");
        index(2L, "삼치 구이", "ON_SALE");
        index(3L, "장어 구이", "ON_SALE");
        index(4L, "가자미 구이", "ON_SALE");
        index(5L, "제주 감귤", "ON_SALE");
        index(6L, "국산 두부", "ON_SALE");
        index(7L, "유기농 우유", "ON_SALE");
        index(8L, "생수 묶음", "ON_SALE");
        index(9L, "백미 쌀", "ON_SALE");
        index(10L, "계란 한판", "ON_SALE");

        assertThat(productSearchRepository.search("고등어 구이", 0, 20).productIds().get(0)).isEqualTo(1L);
    }

    @Test
    @DisplayName("판매중이 아닌 상품은 찾지 않는다")
    void searchesOnSaleOnly() {
        index(1L, "노르웨이 고등어", "ON_SALE");
        index(2L, "노르웨이 고등어", "READY");
        index(3L, "노르웨이 고등어", "SUSPENDED");

        ProductSearchHits hits = productSearchRepository.search("고등어", 0, 20);

        assertThat(hits.productIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("관련도가 같으면 식별자가 큰(최근 등록된) 상품을 앞에 둔다")
    void breaksTiesByLatestProduct() {
        index(1L, "노르웨이 고등어", "ON_SALE");
        index(3L, "노르웨이 고등어", "ON_SALE");
        index(2L, "노르웨이 고등어", "ON_SALE");

        assertThat(productSearchRepository.search("고등어", 0, 20).productIds()).containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("동점 정렬은 식별자를 숫자로 비교한다 - 문자열로 비교하면 9가 10보다 앞에 온다")
    void breaksTiesNumerically() {
        index(9L, "노르웨이 고등어", "ON_SALE");
        index(10L, "노르웨이 고등어", "ON_SALE");

        assertThat(productSearchRepository.search("고등어", 0, 20).productIds()).containsExactly(10L, 9L);
    }

    @Test
    @DisplayName("요청한 페이지만큼 잘라서 돌려주고 전체 건수를 함께 알려준다")
    void paginatesWithTotalHits() {
        for (long id = 1; id <= 5; id++) {
            index(id, "노르웨이 고등어", "ON_SALE");
        }

        ProductSearchHits hits = productSearchRepository.search("고등어", 1, 2);

        assertThat(hits.productIds()).containsExactly(3L, 2L);
        assertThat(hits.totalHits()).isEqualTo(5);
    }

    @Test
    @DisplayName("색인된 상품이 하나도 없는 새 인덱스에서도 오류 없이 빈 결과를 돌려준다")
    void searchesEmptyIndex() {
        // 배포 직후처럼 문서가 들어간 적 없는 인덱스 - 문서가 있으면 ES가 필드를 자동으로 매핑해 문제가 가려진다
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        indexOps.delete();
        indexOps.createWithMapping();

        ProductSearchHits hits = productSearchRepository.search("고등어", 0, 20);

        assertThat(hits.productIds()).isEmpty();
        assertThat(hits.totalHits()).isZero();
    }

    private void index(Long productId, String name, String status) {
        productDocumentRepository.save(ProductDocument.from(
                new ProductChangedEvent(productId, name, null, 2L, 1L, status, 10_000)
        ));
    }
}
