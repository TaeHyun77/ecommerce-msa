package com.park.ecommerce.inbound.reception;

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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({InboundReceptionController.class, InboundInterfaceAdminController.class})
class InboundReceptionControllerTest {
    private static final String EXPECTATION_REQUEST = """
            {
              "asnNo": "ASN-0001",
              "supplierCode": "SUP-001",
              "expectedArrivalAt": "2026-09-22T06:00:00",
              "lines": [ { "productCode": "SKU-0001", "quantity": 200 } ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundReceptionService inboundReceptionService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("입고 예정서를 접수하면 202를 응답한다")
    void acceptsExpectation() throws Exception {
        mockMvc.perform(post("/api/partner/inbound-expectations").contentType(MediaType.APPLICATION_JSON).content(EXPECTATION_REQUEST))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("입고 품목이 비어 있으면 400과 검증 메시지를 응답한다")
    void rejectsEmptyLines() throws Exception {
        String request = EXPECTATION_REQUEST.replace("[ { \"productCode\": \"SKU-0001\", \"quantity\": 200 } ]", "[]");

        mockMvc.perform(post("/api/partner/inbound-expectations").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입고 품목은 1개 이상이어야 합니다."));
    }

    @Test
    @DisplayName("품목의 예정 수량이 0이면 400을 응답한다")
    void rejectsZeroQuantity() throws Exception {
        String request = EXPECTATION_REQUEST.replace("\"quantity\": 200", "\"quantity\": 0");

        mockMvc.perform(post("/api/partner/inbound-expectations").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("예정 수량은 1개 이상이어야 합니다."));
    }

    @Test
    @DisplayName("실패한 예정서를 재처리하면 204를 응답한다")
    void retries() throws Exception {
        mockMvc.perform(post("/api/admin/inbound-interfaces/ASN-0001/retry"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("실패하지 않은 예정서를 재처리하면 409를 응답한다")
    void rejectsRetryWhenNotFailed() throws Exception {
        willThrow(new InboundException(InboundErrorCode.INTERFACE_NOT_FAILED)).given(inboundReceptionService).retry("ASN-0001");

        mockMvc.perform(post("/api/admin/inbound-interfaces/ASN-0001/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERFACE_NOT_FAILED"));
    }

    @Test
    @DisplayName("상태로 목록을 조회하면 실패 사유를 포함해 응답한다")
    void findsByStatus() throws Exception {
        given(inboundReceptionService.findAllByStatus(InboundInterfaceStatus.FAILED)).willReturn(List.of(
                new InboundInterfaceResponse("ASN-0003", InboundInterfaceStatus.FAILED, 10, "미등록 상품: SKU-9999",
                        LocalDateTime.of(2026, 9, 19, 16, 55), LocalDateTime.of(2026, 9, 19, 17, 5))
        ));

        mockMvc.perform(get("/api/admin/inbound-interfaces").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].asnNo").value("ASN-0003"))
                .andExpect(jsonPath("$[0].failReason").value("미등록 상품: SKU-9999"));
    }

    @Test
    @DisplayName("존재하지 않는 상태로 조회하면 400을 응답한다")
    void rejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/admin/inbound-interfaces").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("상태 없이 조회하면 400을 응답한다")
    void rejectsMissingStatus() throws Exception {
        mockMvc.perform(get("/api/admin/inbound-interfaces"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
