package com.park.ecommerce.product.status;

/**
 * 상품 판매 상태
 * 품절은 재고와 어긋나지 않도록 상태로 두지 않고 재고 수량으로 판단 - Product#isSoldOut()
 */
public enum ProductStatus {
    READY, // 판매대기 - 등록 후 첫 입고 확정 전, 첫 입고가 확정되면 ON_SALE로 전환
    ON_SALE, // 판매중
    SUSPENDED // 판매중지 - 다시 ON_SALE로 재개 가능
}
