package com.park.ecommerce.inbound;

import com.park.ecommerce.inbound.dto.InboundRequest;
import com.park.ecommerce.inbound.dto.InboundResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 공급사용 API - 아직 게이트웨이에 라우팅하지 않고 product-service로 직접 호출
@RestController
@RequestMapping("/api/partner/inbounds")
@RequiredArgsConstructor
public class InboundController {
    private final InboundService inboundService;

    // 같은 예정서 번호의 재전송에도 200 - 새로 만든 것인지 다시 처리한 것인지가 아니라 현재 반영 결과를 응답
    @PostMapping
    public InboundResponse receive(@Valid @RequestBody InboundRequest request) {
        return inboundService.receive(request);
    }
}
