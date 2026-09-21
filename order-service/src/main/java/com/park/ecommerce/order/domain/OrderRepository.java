package com.park.ecommerce.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByMemberIdOrderByOrderedAtDesc(Long memberId);

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByOrderNoAndMemberId(String orderNo, Long memberId);

    // 주문번호로 지정한 주문 1건을 CANCELLED로
    // 상태는 조건부 UPDATE로만 바꿈 - 결제 대기가 아닌 주문(결제 중/완료)을 늦게 도착한 취소가 덮어쓰지 않도록
    @Modifying
    @Query("update Order o set o.status = CANCELLED where o.orderNo = :orderNo and o.status = PAYMENT_WAITING")
    int cancelPaymentWaiting(String orderNo);

    // 기한이 지난 모든 결제 대기 주문을 CANCELLED로
    // 재고 복구는 product-service가 선점 만료로 따로 처리하므로 건별 후처리 없이 한 번에 취소
    @Modifying
    @Query("update Order o set o.status = CANCELLED where o.status = PAYMENT_WAITING and o.expiresAt <= :now")
    int cancelExpired(LocalDateTime now);
}
