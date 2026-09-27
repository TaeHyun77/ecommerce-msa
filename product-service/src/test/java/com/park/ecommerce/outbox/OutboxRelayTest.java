package com.park.ecommerce.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {
    private static final String TOPIC = "product.changed";

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventRepository, kafkaTemplate, TOPIC);
    }

    @Test
    @DisplayName("상품 id를 키로 발행하고 발행한 이벤트를 삭제한다")
    void publishesWithProductIdKey() {
        OutboxEvent event = event(1L, 10L);
        given(outboxEventRepository.findTop100ByOrderByIdAsc()).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, "10", "{}")).willReturn(CompletableFuture.completedFuture(null));

        outboxRelay.publishPendingEvents();

        verify(outboxEventRepository).delete(event);
    }

    @Test
    @DisplayName("발행에 실패하면 이번 회차를 중단해 뒤의 이벤트가 먼저 나가지 않는다")
    void stopsAtFailureToKeepOrder() {
        OutboxEvent failed = event(1L, 10L);
        OutboxEvent next = event(2L, 10L);
        given(outboxEventRepository.findTop100ByOrderByIdAsc()).willReturn(List.of(failed, next));
        given(kafkaTemplate.send(TOPIC, "10", "{}"))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 연결 실패")));

        outboxRelay.publishPendingEvents();

        verify(kafkaTemplate).send(anyString(), anyString(), anyString()); // 두 번째 이벤트는 발행을 시도하지 않음
        verify(outboxEventRepository, never()).delete(any()); // 실패한 이벤트와 그 뒤 이벤트 모두 다음 회차에 다시 발행
    }

    @Test
    @DisplayName("브로커에 접속하지 못해 send()가 바로 예외를 던져도 이벤트를 지우지 않고 이번 회차를 중단한다")
    void stopsWhenBrokerUnavailable() {
        given(outboxEventRepository.findTop100ByOrderByIdAsc()).willReturn(List.of(event(1L, 10L), event(2L, 10L)));
        given(kafkaTemplate.send(TOPIC, "10", "{}")).willThrow(new KafkaException("Send failed"));

        outboxRelay.publishPendingEvents(); // 예외가 스케줄러 밖으로 새지 않아야 한다

        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
        verify(outboxEventRepository, never()).delete(any());
    }

    private static OutboxEvent event(Long id, Long productId) {
        OutboxEvent event = new OutboxEvent(productId, "{}", LocalDateTime.now());
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
