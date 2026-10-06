package com.park.ecommerce.inbound;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InboundRepository extends JpaRepository<Inbound, Long> {
    // 같은 예정서가 동시에 들어와도 한 요청만 재고를 반영하도록 행을 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inbound i where i.asnNo = :asnNo")
    Optional<Inbound> findByAsnNoForUpdate(String asnNo);
}
