package com.park.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

// Kafka 메시지를 처리하다가 예외가 나면 어떻게 할지 정하는 설정
@Configuration
public class KafkaConsumerConfig {
    private static final long RETRY_INTERVAL_MS = 5_000;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // ES 저장 실패 같은 일반 예외는 5초 간격으로 성공할 때까지 계속 재시도
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, FixedBackOff.UNLIMITED_ATTEMPTS));

        // JacksonException(JSON 형식 오류): 재시도하지 않고 바로 로그만 남긴 뒤 다음 메시지로 넘어감
        errorHandler.addNotRetryableExceptions(JacksonException.class);
        return errorHandler;
    }
}