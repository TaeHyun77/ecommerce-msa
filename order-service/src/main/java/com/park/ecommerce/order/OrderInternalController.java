package com.park.ecommerce.order;

import com.park.ecommerce.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 게이트웨이가 외부로 노출하지 않는 서비스 간 내부 통신 전용 API - 인프라(게이트웨이 라우팅 설정)에서 별도로 차단해야 한다.
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class OrderInternalController {
    private final OrderService orderService;

    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam Long memberId) {
        return orderService.getOrdersByMemberId(memberId);
    }
}
