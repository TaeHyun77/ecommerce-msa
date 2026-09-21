package com.park.ecommerce.inbound.receipt;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InboundReceiptController.class)
class InboundReceiptControllerTest {
    private static final String RECEIPT_REQUEST = """
            {
              "receiptNo": "RCV-0001",
              "asnNo": "ASN-0001",
              "lines": [ { "productCode": "SKU-0001", "acceptedQuantity": 195, "rejectedQuantity": 5 } ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundReceiptService inboundReceiptService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("입고 확정을 반영하면 204를 응답한다")
    void receives() throws Exception {
        mockMvc.perform(post("/internal/inbound-receipts").contentType(MediaType.APPLICATION_JSON).content(RECEIPT_REQUEST))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("다른 확정 번호로 이미 완료된 입고 예정이면 409를 응답한다")
    void rejectsAnotherReceipt() throws Exception {
        willThrow(new InboundException(InboundErrorCode.ALREADY_RECEIVED)).given(inboundReceiptService).receive(any());

        mockMvc.perform(post("/internal/inbound-receipts").contentType(MediaType.APPLICATION_JSON).content(RECEIPT_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RECEIVED"));
    }
}
