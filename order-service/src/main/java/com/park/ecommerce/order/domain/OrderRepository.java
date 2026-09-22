package com.park.ecommerce.order.domain;

import org.springframework.data.domain.Pageable;
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

    // 결제 대기이고 결제 기한 전인 주문만 승인 중으로 바꿈
    // 같은 주문의 승인 요청이 동시에 오거나 만료 취소와 겹쳐도 한쪽만 반영되고, 결제 기한이 지난 주문은 만료 취소 전이어도 승인하지 않음
    @Modifying
    @Query("update Order o set o.status = APPROVING, o.paymentKey = :paymentKey, o.approvalRequestedAt = :now where o.orderNo = :orderNo and o.status = PAYMENT_WAITING and o.expiresAt > :now")
    int startApproval(String orderNo, String paymentKey, LocalDateTime now);

    @Modifying
    @Query("update Order o set o.status = PAID where o.orderNo = :orderNo and o.status = APPROVING")
    int markPaid(String orderNo);

    @Modifying
    @Query("update Order o set o.status = CANCELLED where o.orderNo = :orderNo and o.status = APPROVING")
    int cancelApproving(String orderNo);

    @Query("select o.orderNo from Order o where o.status = APPROVING and o.approvalRequestedAt <= :before order by o.id")
    List<String> findStaleApprovingOrderNos(LocalDateTime before, Pageable pageable);
}
