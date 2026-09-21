package com.park.ecommerce.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {
    private final OrderService orderService;

    // 주기마다 결제 기한이 지난 결제 대기 주문을 취소 상태로 변경
    @Scheduled(fixedDelayString = "${order.payment-waiting.expire-poll-delay}")
    public void cancelExpiredOrders() {
        int cancelled = orderService.cancelExpiredOrders();
        if (cancelled > 0) {
            log.info("[주문] 결제 기한 만료로 주문 취소 {}건", cancelled);
        }
    }
}
