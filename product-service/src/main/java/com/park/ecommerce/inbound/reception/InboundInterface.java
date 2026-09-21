package com.park.ecommerce.inbound.reception;

import com.park.ecommerce.common.BaseTimeEntity;
import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// 공급사가 보낸 입고 예정서 원본과 처리 상태
// 수신과 처리를 분리하고, 기반 정보(상품)가 준비될 때까지 재시도하기 위한 대기열 역할
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class InboundInterface extends BaseTimeEntity {
    private static final int FAIL_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String asnNo; // 입고 예정서 번호 - 공급사 재전송 시 중복 수신을 막는 기준

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // 수신한 요청 JSON 원본 - 변환 로직 수정 후 재처리할 수 있도록 보존

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundInterfaceStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(length = FAIL_REASON_MAX_LENGTH)
    private String failReason;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    public InboundInterface(String asnNo, String payload, LocalDateTime receivedAt) {
        this.asnNo = asnNo;
        this.payload = payload;
        this.status = InboundInterfaceStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = receivedAt; // 수신 직후 다음 스케줄러 실행에서 바로 처리
        this.receivedAt = receivedAt;
    }

    public void markDone() {
        this.status = InboundInterfaceStatus.DONE;
        this.failReason = null;
    }

    // 최초 시도 1회 + 재시도 maxRetry회까지 허용하고, 이후 실패는 FAILED로 확정
    public void retryLater(String reason, LocalDateTime now, Duration interval, int maxRetry) {
        if (retryCount >= maxRetry) {
            fail(reason);
            return;
        }
        this.retryCount++;
        this.nextRetryAt = now.plus(interval);
        this.failReason = truncate(reason);
    }

    public void fail(String reason) {
        this.status = InboundInterfaceStatus.FAILED;
        this.failReason = truncate(reason);
    }

    // 담당자가 원인(상품 미등록 등)을 해결한 뒤 다시 처리하도록 되돌림
    // 실패 사유는 다음 처리에서 성공하면 지워지도록 남겨 둔다 - 재실패 시 이전 원인과 비교할 수 있도록
    public void retryManually(LocalDateTime now) {
        validateFailed();
        resetForRetry(now);
    }

    // 공급사가 잘못된 내용(상품코드 오기 등)을 고쳐 같은 예정서 번호로 다시 보낸 경우
    public void resubmit(String payload, LocalDateTime now) {
        validateFailed();
        this.payload = payload;
        resetForRetry(now);
    }

    public boolean isPending() {
        return status == InboundInterfaceStatus.PENDING;
    }

    public boolean isFailed() {
        return status == InboundInterfaceStatus.FAILED;
    }

    // PENDING·DONE을 되돌리면 처리 중인 건이 중복 처리되거나 이미 만든 입고 예정이 다시 생성될 수 있음
    private void validateFailed() {
        if (!isFailed()) {
            throw new InboundException(InboundErrorCode.INTERFACE_NOT_FAILED);
        }
    }

    private void resetForRetry(LocalDateTime now) {
        this.status = InboundInterfaceStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = now;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= FAIL_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, FAIL_REASON_MAX_LENGTH);
    }
}
