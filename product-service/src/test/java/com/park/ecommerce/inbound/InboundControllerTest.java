package com.park.ecommerce.inbound;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import com.park.ecommerce.inbound.dto.InboundResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InboundController.class)
class InboundControllerTest {
    private static final String INBOUND_REQUEST = """
            {
              "asnNo": "ASN-0001",
              "supplierCode": "SUP-001",
              "lines": [ { "productCode": "SKU-0001", "quantity": 200 } ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundService inboundService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("입고 예정서를 접수하면 200과 반영 결과를 응답한다")
    void receivesInbound() throws Exception {
        given(inboundService.receive(org.mockito.ArgumentMatchers.any())).willReturn(new InboundResponse(
                "ASN-0001", InboundStatus.INCOMPLETE, 1,
                List.of(new InboundResponse.FailedLine("SKU-9999", "등록되지 않은 상품입니다."))
        ));

        mockMvc.perform(post("/api/partner/inbounds").contentType(MediaType.APPLICATION_JSON).content(INBOUND_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asnNo").value("ASN-0001"))
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.receivedCount").value(1))
                .andExpect(jsonPath("$.failedLines[0].productCode").value("SKU-9999"))
                .andExpect(jsonPath("$.failedLines[0].reason").value("등록되지 않은 상품입니다."));
    }

    @Test
    @DisplayName("입고 품목이 비어 있으면 400과 검증 메시지를 응답한다")
    void rejectsEmptyLines() throws Exception {
        String request = INBOUND_REQUEST.replace("[ { \"productCode\": \"SKU-0001\", \"quantity\": 200 } ]", "[]");

        mockMvc.perform(post("/api/partner/inbounds").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입고 품목은 1개 이상이어야 합니다."));
    }

    @Test
    @DisplayName("품목의 예정 수량이 0이면 400을 응답한다")
    void rejectsZeroQuantity() throws Exception {
        String request = INBOUND_REQUEST.replace("\"quantity\": 200", "\"quantity\": 0");

        mockMvc.perform(post("/api/partner/inbounds").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("예정 수량은 1개 이상이어야 합니다."));
    }

    @Test
    @DisplayName("같은 상품이 여러 품목에 있으면 400을 응답한다")
    void rejectsDuplicateProduct() throws Exception {
        given(inboundService.receive(org.mockito.ArgumentMatchers.any()))
                .willThrow(new InboundException(InboundErrorCode.DUPLICATE_LINE_PRODUCT));

        mockMvc.perform(post("/api/partner/inbounds").contentType(MediaType.APPLICATION_JSON).content(INBOUND_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LINE_PRODUCT"));
    }
}
