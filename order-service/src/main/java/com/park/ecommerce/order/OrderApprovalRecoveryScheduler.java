package com.park.ecommerce.order;

import com.park.ecommerce.order.domain.OrderRepository;
import com.park.ecommerce.order.domain.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 결과를 모른 채 승인 중(APPROVING)으로 멈춘 주문을 주기적으로 다시 처리해 결제 완료 또는 취소로 확정함
@Slf4j
@Component
public class OrderApprovalRecoveryScheduler {
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderPaymentService orderPaymentService;
    private final Duration staleAfter;

    public OrderApprovalRecoveryScheduler(
            OrderRepository orderRepository,
            OrderPaymentService orderPaymentService,
            @Value("${order.approval-recovery.stale-after}") Duration staleAfter
    ) {
        this.orderRepository = orderRepository;
        this.orderPaymentService = orderPaymentService;
        this.staleAfter = staleAfter;
    }

    // 승인을 시작한 지 staleAfter가 지난 주문만 대상 - 아직 응답을 기다리는 요청과 같은 주문을 동시에 처리하지 않도록
    @Scheduled(fixedDelayString = "${order.approval-recovery.poll-delay}")
    public void recoverStaleApprovals() {
        List<String> orderNos = orderRepository.findStaleApprovingOrderNos(
                LocalDateTime.now().minus(staleAfter), PageRequest.of(0, BATCH_SIZE)
        );

        for (String orderNo : orderNos) {
            try {
                OrderStatus status = orderPaymentService.recover(orderNo);
                log.info("[결제 복구] orderNo={}, 결과={}", orderNo, status);
            } catch (RuntimeException e) {
                // 한 건의 실패가 같은 회차의 나머지 건 처리를 막지 않도록 건 단위로 격리 - 실패한 건은 다음 회차에 다시 대상이 됨
                log.error("[결제 복구] 예상하지 못한 오류 orderNo={}", orderNo, e);
            }
        }
    }
}
