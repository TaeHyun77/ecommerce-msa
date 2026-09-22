package com.park.ecommerce.order;

import com.park.ecommerce.exception.OrderErrorCode;
import com.park.ecommerce.exception.OrderException;
import com.park.ecommerce.order.domain.Order;
import com.park.ecommerce.order.domain.OrderRepository;
import com.park.ecommerce.order.dto.OrderDetailResponse;
import com.park.ecommerce.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;

    // 특정 사용자의 주문 목록 조회
    public List<OrderResponse> getOrdersByMemberId(Long memberId) {
        return orderRepository.findAllByMemberIdOrderByOrderedAtDesc(memberId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    // 주문 조회
    // 다른 회원의 주문은 존재 여부도 드러내지 않도록 없는 주문과 같은 404로 응답
    public OrderDetailResponse getOrder(Long memberId, String orderNo) {
        Order order = orderRepository.findByOrderNoAndMemberId(orderNo, memberId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        return OrderDetailResponse.from(order);
    }

    @Transactional
    public void cancel(String orderNo) {
        orderRepository.cancelPaymentWaiting(orderNo);
    }

    // 승인 중으로 바꾼 뒤 바로 커밋해, 이후 원격 호출 중에 서버가 멈춰도 복구 스케줄러가 이 주문을 찾을 수 있도록 한다
    @Transactional
    public boolean startApproval(String orderNo, String paymentKey) {
        return orderRepository.startApproval(orderNo, paymentKey, LocalDateTime.now()) == 1;
    }

    @Transactional
    public void markPaid(String orderNo) {
        orderRepository.markPaid(orderNo);
    }

    @Transactional
    public void cancelApproving(String orderNo) {
        orderRepository.cancelApproving(orderNo);
    }

    @Transactional
    public int cancelExpiredOrders() {
        return orderRepository.cancelExpired(LocalDateTime.now());
    }
}
