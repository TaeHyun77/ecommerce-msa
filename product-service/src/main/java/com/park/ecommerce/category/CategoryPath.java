package com.park.ecommerce.category;

// 하위 카테고리 이름은 상위가 다르면 겹칠 수 있기에 상위명과 함께 식별
public record CategoryPath(String parentName, String name) {
}
