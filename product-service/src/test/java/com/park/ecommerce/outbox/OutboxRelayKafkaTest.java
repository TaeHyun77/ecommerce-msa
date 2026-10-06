package com.park.ecommerce.outbox;

import com.park.ecommerce.category.Category;
import com.park.ecommerce.category.CategoryRepository;
import com.park.ecommerce.product.ProductService;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.status.StorageType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 커밋된 상품 변경이 실제 브로커까지 도달하는지는 Kafka가 있어야 확인되므로 MySQL과 Kafka 컨테이너로 검증
@Testcontainers
@SpringBootTest(properties = { // 릴레이는 테스트에서 직접 호출
        "stock.reservation.expire-poll-delay=1h",
        "outbox.relay.poll-delay=1h"
})
class OutboxRelayKafkaTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.1");

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Test
    @DisplayName("판매가 시작된 상품을 릴레이가 상품 id를 키로 토픽에 발행하고 outbox에서 삭제한다")
    void publishesSaleStartedProduct() {
        Category parent = categoryRepository.save(Category.builder().name("해산물").build());
        Long categoryId = categoryRepository.save(Category.builder().name("생선").parentId(parent.getId()).build()).getId();
        Long productId = productService.register(new ProductCreateRequest(
                "SKU-" + UUID.randomUUID(), "노르웨이 순살 고등어 1손", "파크씨푸드", null,
                StorageType.FROZEN, 8_900, null, categoryId
        )).productId();
        productService.receiveStock(productId, 10); // 첫 입고로 판매 시작 - 입고 확정 흐름 대신 재고 반영만 직접 호출

        outboxRelay.publishPendingEvents();

        ConsumerRecord<String, String> record = pollOne(String.valueOf(productId));
        assertThat(record.value()).contains("\"status\":\"ON_SALE\"", "\"parentCategoryId\":" + parent.getId());
        assertThat(outboxEventRepository.count()).isZero();
    }

    private ConsumerRecord<String, String> pollOne(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("product.changed"));
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 10_000;
            while (records.isEmpty() && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (record.key().equals(key)) records.add(record);
                });
            }
            assertThat(records).as("토픽에서 상품 %s의 메시지를 받아야 한다", key).hasSize(1);
            return records.get(0);
        }
    }
}
