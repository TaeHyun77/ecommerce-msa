package com.park.ecommerce.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicUpdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고를 상품 엔티티에 합쳤을 때, 상품 정보 수정이 그 사이 커밋된 재고 차감을 덮어쓰는지 확인하는 학습 테스트
 * 관리자 수정(T1)이 상품을 조회한 뒤 커밋하기 전에 주문 차감(T2)이 커밋되는 순서를 REQUIRES_NEW로 매번 같게 재현한다.
 */
@Slf4j
@Testcontainers
@SpringBootTest(properties = {
        "inbound.interface.poll-delay=1h", // 테스트 중 스케줄러 개입 방지
        "logging.level.org.hibernate.SQL=debug" // UPDATE에 어떤 컬럼이 들어가는지 로그로 확인
})
class StockLostUpdateLearningTest {
    private static final int INITIAL_STOCK = 10;

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("기본 설정이면 이름만 바꿔도 모든 컬럼을 UPDATE해 그 사이 커밋된 재고 차감을 덮어쓴다")
    void defaultOverwritesStock() {
        int stock = renameWhileStockDecreased(new DefaultProduct("before", INITIAL_STOCK), "learning_default_product");

        assertThat(stock).isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("@DynamicUpdate면 바뀐 이름만 UPDATE해 재고 차감이 유지된다")
    void dynamicUpdateKeepsStock() {
        int stock = renameWhileStockDecreased(new DynamicUpdateProduct("before", INITIAL_STOCK), "learning_dynamic_update_product");

        assertThat(stock).isEqualTo(INITIAL_STOCK - 1);
    }

    @Test
    @DisplayName("재고 컬럼이 updatable = false면 엔티티 UPDATE에서 빠지고, JPQL 차감은 그대로 반영된다")
    void notUpdatableKeepsStock() {
        int stock = renameWhileStockDecreased(new NotUpdatableProduct("before", INITIAL_STOCK), "learning_not_updatable_product");

        assertThat(stock).isEqualTo(INITIAL_STOCK - 1);
    }

    private int renameWhileStockDecreased(StockProduct product, String tableName) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 중첩 클래스라 엔티티 이름이 "외부클래스$엔티티"가 되므로 JPQL에는 메타모델의 이름을, 로그에는 클래스 이름을 쓴다
        String entityName = em.getMetamodel().entity(product.getClass()).getName();
        String label = product.getClass().getSimpleName();

        tx.executeWithoutResult(status -> em.persist(product));
        Long id = product.getId();
        log.info("[{}] 상품 등록 - 재고 {}", label, INITIAL_STOCK);

        tx.executeWithoutResult(status -> {
            StockProduct loaded = em.find(product.getClass(), id);
            log.info("[{}] T1(관리자) 상품 조회 - 메모리에 올라온 재고 {}", label, loaded.getQuantity());

            // T1은 조회만 해서 락이 없으므로 T2의 차감은 기다리지 않고 바로 커밋된다
            newTx.executeWithoutResult(innerStatus -> em
                    .createQuery("update " + entityName + " p set p.quantity = p.quantity - 1 where p.id = :id")
                    .setParameter("id", id)
                    .executeUpdate());
            log.info("[{}] T2(주문) 재고 1개 차감 커밋", label);

            loaded.rename("after");
            log.info("[{}] T1(관리자) 이름만 변경 - 커밋 시 변경 감지로 UPDATE", label);
        });

        // 영속성 컨텍스트에 남은 값이 아니라 DB에 실제로 기록된 값을 확인
        int stock = jdbcTemplate.queryForObject("select quantity from " + tableName + " where id = ?", Integer.class, id);
        log.info("[{}] 최종 재고 {} (차감이 반영됐다면 {})", label, stock, INITIAL_STOCK - 1);
        return stock;
    }

    interface StockProduct {
        Long getId();

        int getQuantity();

        void rename(String name);
    }

    // 재고를 바꾸는 메서드 없이 이름 변경 메서드만 둔다 - 세 엔티티의 차이는 어노테이션뿐
    @Entity
    @Table(name = "learning_default_product")
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class DefaultProduct implements StockProduct {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        private int quantity;

        DefaultProduct(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @Override
        public void rename(String name) {
            this.name = name;
        }
    }

    @Entity
    @DynamicUpdate
    @Table(name = "learning_dynamic_update_product")
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class DynamicUpdateProduct implements StockProduct {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        private int quantity;

        DynamicUpdateProduct(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @Override
        public void rename(String name) {
            this.name = name;
        }
    }

    @Entity
    @Table(name = "learning_not_updatable_product")
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class NotUpdatableProduct implements StockProduct {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        // @Column을 쓰면 기본형이어도 NOT NULL이 빠지므로 명시
        @Column(nullable = false, updatable = false)
        private int quantity;

        NotUpdatableProduct(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @Override
        public void rename(String name) {
            this.name = name;
        }
    }
}
