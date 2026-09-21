package com.park.ecommerce.reservation.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 확정/해제/만료가 같은 선점에 동시에 와도 조건을 만족한 한쪽만 반영되도록 상태 변경은 조건부 UPDATE로만 함
// 벌크 연산은 감사를 거치지 않으므로 updatedAt을 직접 갱신
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    boolean existsByOrderNo(String orderNo);

    Optional<StockReservation> findByOrderNo(String orderNo);

    @Modifying
    @Query("update StockReservation r set r.status = CONFIRMED, r.updatedAt = current_timestamp where r.orderNo = :orderNo and r.status = RESERVED and r.expiresAt > :now")
    int confirm(String orderNo, LocalDateTime now);

    // 확정된 선점도 해제 대상 - PG 승인이 거절되면 확정을 되돌려야 하므로
    @Modifying
    @Query("update StockReservation r set r.status = RELEASED, r.updatedAt = current_timestamp where r.orderNo = :orderNo and r.status in (RESERVED, CONFIRMED)")
    int release(String orderNo);

    // 확정된 선점은 PG 승인 중이므로 만료 대상에서 제외
    @Query("select r.id from StockReservation r where r.status = RESERVED and r.expiresAt <= :now order by r.id")
    List<Long> findExpiredIds(LocalDateTime now, Pageable pageable);

    // 스케줄러가 대상을 조회한 뒤 그 사이 확정/해제됐을 수 있어 같은 조건을 UPDATE에서 다시 확인
    @Modifying
    @Query("update StockReservation r set r.status = RELEASED, r.updatedAt = current_timestamp where r.id = :id and r.status = RESERVED and r.expiresAt <= :now")
    int expire(Long id, LocalDateTime now);
}
