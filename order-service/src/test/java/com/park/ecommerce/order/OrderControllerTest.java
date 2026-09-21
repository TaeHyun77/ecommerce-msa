package com.park.ecommerce.order;

import com.park.ecommerce.exception.OrderErrorCode;
import com.park.ecommerce.exception.OrderException;
import com.park.ecommerce.order.domain.OrderStatus;
import com.park.ecommerce.order.dto.OrderCreateRequest;
import com.park.ecommerce.order.dto.OrderCreateResponse;
import com.park.ecommerce.order.dto.OrderDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    private static final String MEMBER_ID_HEADER = "X-Member-Id";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 22, 12, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPlacementService orderPlacementService;

    @MockitoBean
    private OrderService orderService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("주문하면 201과 결제창에 필요한 주문번호·주문명·금액·결제 기한을 응답한다")
    void createsOrder() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(1L, 2)));
        given(orderPlacementService.place(1L, request))
                .willReturn(new OrderCreateResponse("ORDER-0001", "유기농 우유 900ml", 7_800, EXPIRES_AT));

        mockMvc.perform(post("/api/orders")
                        .header(MEMBER_ID_HEADER, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequest(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo").value("ORDER-0001"))
                .andExpect(jsonPath("$.orderName").value("유기농 우유 900ml"))
                .andExpect(jsonPath("$.amount").value(7_800))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-22T12:10:00"));
    }

    @Test
    @DisplayName("품목 수량이 1회 구매 제한(99개)을 넘으면 400을 응답한다")
    void rejectsQuantityOverLimit() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(MEMBER_ID_HEADER, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequest(100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("주문 품목이 비어 있으면 400을 응답한다")
    void rejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(MEMBER_ID_HEADER, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("회원 식별 헤더가 없으면 400을 응답한다")
    void rejectsMissingMemberHeader() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequest(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("주문을 조회하면 200과 주문 상태·품목을 응답한다")
    void getsOrder() throws Exception {
        given(orderService.getOrder(1L, "ORDER-0001")).willReturn(new OrderDetailResponse(
                "ORDER-0001", OrderStatus.PAYMENT_WAITING, "유기농 우유 900ml", 7_800, EXPIRES_AT.minusMinutes(10), EXPIRES_AT,
                List.of(new OrderDetailResponse.Line(1L, "유기농 우유 900ml", 3_900, 2))
        ));

        mockMvc.perform(get("/api/orders/ORDER-0001").header(MEMBER_ID_HEADER, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_WAITING"))
                .andExpect(jsonPath("$.lines[0].productName").value("유기농 우유 900ml"));
    }

    @Test
    @DisplayName("본인 주문이 아니거나 없는 주문을 조회하면 404를 응답한다")
    void respondsNotFoundForOthersOrder() throws Exception {
        given(orderService.getOrder(1L, "ORDER-0001")).willThrow(new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(get("/api/orders/ORDER-0001").header(MEMBER_ID_HEADER, 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private static String orderRequest(int quantity) {
        return """
                { "items": [ { "productId": 1, "quantity": %d } ] }
                """.formatted(quantity);
    }
}
