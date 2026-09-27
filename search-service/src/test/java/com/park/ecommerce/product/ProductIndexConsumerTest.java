package com.park.ecommerce.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

// 이벤트 수신부터 nori 분석기로 색인되기까지는 실제 Kafka와 ES가 있어야 확인되므로 컨테이너로 검증
@Testcontainers
@SpringBootTest
class ProductIndexConsumerTest {
    private static final String TOPIC = "product.changed";

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.1");

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
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProductDocumentRepository productDocumentRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Test
    @DisplayName("같은 상품의 중복·상태 변경 이벤트를 차례로 받으면 마지막 스냅샷으로 덮어쓴다")
    void overwritesSameProduct() {
        send(1L, event(1L, "노르웨이 순살 고등어 1손", "READY"));
        send(1L, event(1L, "노르웨이 순살 고등어 1손", "READY")); // 릴레이 재발행으로 들어온 중복
        send(1L, event(1L, "노르웨이 순살 고등어 1손", "ON_SALE"));

        ProductDocument document = awaitDocument(1L, doc -> doc.getStatus().equals("ON_SALE"));

        assertThat(document.getParentCategoryId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("상품명은 형태소 단위로 색인되어 명사 하나로도 검색된다")
    void searchesByNoun() {
        send(2L, event(2L, "제주 은갈치 2마리", "ON_SALE"));
        awaitDocument(2L, doc -> true);

        var hits = elasticsearchOperations.search(new CriteriaQuery(new Criteria("name").matches("갈치")), ProductDocument.class);

        assertThat(hits.getSearchHits()).extracting(SearchHit::getId).contains("2");
    }

    @Test
    @DisplayName("형식이 잘못된 메시지는 건너뛰고 뒤의 메시지를 계속 색인한다")
    void skipsMalformedMessage() {
        kafkaTemplate.send(TOPIC, "3", "{잘못된 JSON");
        send(3L, event(3L, "완도 활전복 1kg", "ON_SALE"));

        assertThat(awaitDocument(3L, doc -> true).getName()).isEqualTo("완도 활전복 1kg");
    }

    private void send(Long productId, String payload) {
        kafkaTemplate.send(TOPIC, String.valueOf(productId), payload).join();
    }

    private ProductDocument awaitDocument(Long productId, Predicate<ProductDocument> condition) {
        long deadline = System.currentTimeMillis() + 30_000; // 첫 구독 시 토픽 생성과 파티션 할당까지 걸리는 시간을 고려
        while (System.currentTimeMillis() < deadline) {
            Optional<ProductDocument> document = productDocumentRepository.findById(productId).filter(condition);
            if (document.isPresent()) return document.get();
            sleep();
        }
        throw new AssertionError("상품 " + productId + "이 기대한 상태로 색인되지 않았다");
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String event(Long productId, String name, String status) {
        return """
                {"productId":%d,"name":"%s","brand":"파크씨푸드","categoryId":11,"parentCategoryId":10,"status":"%s","price":8900}
                """.formatted(productId, name, status);
    }
}
