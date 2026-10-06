package com.park.ecommerce.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class OutboxRelay {
    private static final long SEND_TIMEOUT_SECONDS = 5;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.relay.topic}") String topic
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    // 이전 실행이 끝난 뒤 outbox.relay.poll-delay 초를 기다림
    @Scheduled(fixedDelayString = "${outbox.relay.poll-delay}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findTop100ByOrderByIdAsc();

        for (OutboxEvent event : events) {
            try {
                // 한 건씩 발행 완료를 기다려야 실패한 이벤트를 건너뛰고 뒤의 이벤트가 먼저 나가는 일이 없다
                kafkaTemplate.send(topic, String.valueOf(event.getAggregateId()), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (KafkaException | ExecutionException | TimeoutException e) {
                // 브로커에 접속하지 못하면 send()가 Future를 돌려주지 않고 KafkaException을 바로 던지므로 함께 처리
                log.warn("[아웃박스 발행] 발행 실패로 이번 회차 중단 eventId={}, 사유={}", event.getId(), e.getMessage());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 발행 이력은 Kafka 토픽에 남으므로 행은 지움
            outboxEventRepository.delete(event);
        }
    }
}