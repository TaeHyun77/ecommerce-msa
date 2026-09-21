package com.park.ecommerce.inbound.reception;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundInterfaceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 10, 0);
    private static final Duration INTERVAL = Duration.ofMinutes(1);

    @Test
    @DisplayName("수신 직후에는 처리 대기 상태이고 바로 처리 대상이 된다")
    void startsPending() {
        InboundInterface inboundInterface = new InboundInterface("ASN-0001", "{}", NOW);

        assertThat(inboundInterface.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);
        assertThat(inboundInterface.getRetryCount()).isZero();
        assertThat(inboundInterface.getNextRetryAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("재시도하면 횟수가 늘고 다음 시도 시각이 간격만큼 밀린다")
    void schedulesRetry() {
        InboundInterface inboundInterface = new InboundInterface("ASN-0001", "{}", NOW);

        inboundInterface.retryLater("미등록 상품: SKU-0005", NOW, INTERVAL, 3);

        assertThat(inboundInterface.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);
        assertThat(inboundInterface.getRetryCount()).isEqualTo(1);
        assertThat(inboundInterface.getNextRetryAt()).isEqualTo(NOW.plus(INTERVAL));
        assertThat(inboundInterface.getFailReason()).isEqualTo("미등록 상품: SKU-0005");
    }

    @Test
    @DisplayName("재시도를 최대 횟수만큼 한 뒤 다시 실패하면 실패로 확정된다")
    void failsAfterMaxRetry() {
        InboundInterface inboundInterface = new InboundInterface("ASN-0001", "{}", NOW);

        inboundInterface.retryLater("사유", NOW, INTERVAL, 2);
        inboundInterface.retryLater("사유", NOW, INTERVAL, 2);
        assertThat(inboundInterface.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);

        inboundInterface.retryLater("사유", NOW, INTERVAL, 2);

        assertThat(inboundInterface.getStatus()).isEqualTo(InboundInterfaceStatus.FAILED);
        assertThat(inboundInterface.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("처리에 성공하면 완료 상태가 되고 이전 실패 사유는 지워진다")
    void marksDone() {
        InboundInterface inboundInterface = new InboundInterface("ASN-0001", "{}", NOW);
        inboundInterface.retryLater("미등록 상품: SKU-0005", NOW, INTERVAL, 3);

        inboundInterface.markDone();

        assertThat(inboundInterface.getStatus()).isEqualTo(InboundInterfaceStatus.DONE);
        assertThat(inboundInterface.getFailReason()).isNull();
    }

    @Test
    @DisplayName("실패 사유가 길면 컬럼 길이에 맞춰 자른다")
    void truncatesLongReason() {
        InboundInterface inboundInterface = new InboundInterface("ASN-0001", "{}", NOW);

        inboundInterface.fail("가".repeat(600));

        assertThat(inboundInterface.getFailReason()).hasSize(500);
    }

    @Test
    @DisplayName("실패한 건을 수동 재처리하면 재시도 횟수가 초기화되고 바로 처리 대상이 된다")
    void retriesManually() {
        InboundInterface inboundInterface = exhausted();
        LocalDateTime later = NOW.plusHours(1);

        inboundInterface.retryManually(later);

        assertThat(inboundInterface.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);
        assertThat(inboundInterface.getRetryCount()).isZero();
        assertThat(inboundInterface.getNextRetryAt()).isEqualTo(later);
        assertThat(inboundInterface.getFailReason()).isEqualTo("미등록 상품: SKU-0005");
    }

    @Test
    @DisplayName("정정 재수신하면 원본이 교체되고 처리 대기로 돌아간다")
    void resubmits() {
        InboundInterface inboundInterface = exhausted();

        inboundInterface.resubmit("{\"corrected\":true}", NOW);

        assertThat(inboundInterface.getPayload()).isEqualTo("{\"corrected\":true}");
        assertThat(inboundInterface.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);
        assertThat(inboundInterface.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("실패하지 않은 건은 재처리할 수 없다")
    void rejectsRetryWhenNotFailed() {
        InboundInterface inboundInterface = new InboundInterface("ASN-0001", "{}", NOW);

        assertThatThrownBy(() -> inboundInterface.retryManually(NOW))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.INTERFACE_NOT_FAILED);
    }

    private static InboundInterface exhausted() {
        InboundInterface inboundInterface = new InboundInterface("ASN-0001", "{}", NOW);
        inboundInterface.retryLater("미등록 상품: SKU-0005", NOW, INTERVAL, 1);
        inboundInterface.retryLater("미등록 상품: SKU-0005", NOW, INTERVAL, 1);
        return inboundInterface;
    }
}
