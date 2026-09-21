package com.park.ecommerce.inbound.reception;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InboundReceptionServiceTest {
    @Mock
    private InboundInterfaceRepository inboundInterfaceRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private InboundReceptionService inboundReceptionService;

    @BeforeEach
    void setUp() {
        inboundReceptionService = new InboundReceptionService(inboundInterfaceRepository, jsonMapper);
    }

    @Test
    @DisplayName("입고 예정서를 원본 JSON 그대로 처리 대기 상태로 저장한다")
    void savesPayload() {
        InboundExpectationRequest request = request(List.of(new InboundExpectationRequest.Line("SKU-0001", 200)));
        given(inboundInterfaceRepository.findByAsnNo("ASN-0001")).willReturn(Optional.empty());

        inboundReceptionService.receive(request);

        ArgumentCaptor<InboundInterface> captor = ArgumentCaptor.forClass(InboundInterface.class);
        verify(inboundInterfaceRepository).save(captor.capture());
        InboundInterface saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);
        assertThat(jsonMapper.readValue(saved.getPayload(), InboundExpectationRequest.class)).isEqualTo(request);
    }

    @Test
    @DisplayName("처리 대기 중인 예정서 번호가 다시 오면 원본을 바꾸지 않고 정상 종료한다")
    void ignoresDuplicateAsn() {
        InboundInterface pending = new InboundInterface("ASN-0001", "{\"original\":true}", LocalDateTime.now());
        given(inboundInterfaceRepository.findByAsnNo("ASN-0001")).willReturn(Optional.of(pending));

        inboundReceptionService.receive(request(List.of(new InboundExpectationRequest.Line("SKU-0001", 200))));

        verify(inboundInterfaceRepository, never()).save(any());
        assertThat(pending.getPayload()).isEqualTo("{\"original\":true}");
    }

    @Test
    @DisplayName("실패한 예정서 번호가 다시 오면 정정본으로 원본을 교체하고 다시 처리 대기로 돌린다")
    void resubmitsFailedAsn() {
        InboundInterface failed = new InboundInterface("ASN-0001", "{\"original\":true}", LocalDateTime.now());
        failed.fail("미등록 상품: SKU-00001");
        given(inboundInterfaceRepository.findByAsnNo("ASN-0001")).willReturn(Optional.of(failed));
        InboundExpectationRequest corrected = request(List.of(new InboundExpectationRequest.Line("SKU-0001", 200)));

        inboundReceptionService.receive(corrected);

        verify(inboundInterfaceRepository, never()).save(any());
        assertThat(failed.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);
        assertThat(jsonMapper.readValue(failed.getPayload(), InboundExpectationRequest.class)).isEqualTo(corrected);
    }

    @Test
    @DisplayName("실패한 예정서를 수동 재처리하면 처리 대기로 돌아간다")
    void retriesFailedAsn() {
        InboundInterface failed = new InboundInterface("ASN-0001", "{}", LocalDateTime.now());
        failed.fail("미등록 상품: SKU-0005");
        given(inboundInterfaceRepository.findByAsnNo("ASN-0001")).willReturn(Optional.of(failed));

        inboundReceptionService.retry("ASN-0001");

        assertThat(failed.getStatus()).isEqualTo(InboundInterfaceStatus.PENDING);
        assertThat(failed.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("없는 예정서 번호를 재처리하면 예외가 발생한다")
    void rejectsRetryOfUnknownAsn() {
        given(inboundInterfaceRepository.findByAsnNo("ASN-0001")).willReturn(Optional.empty());

        assertThatThrownBy(() -> inboundReceptionService.retry("ASN-0001"))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.INTERFACE_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 상품이 여러 품목에 있으면 예외가 발생한다")
    void rejectsDuplicateLine() {
        InboundExpectationRequest request = request(List.of(
                new InboundExpectationRequest.Line("SKU-0001", 100),
                new InboundExpectationRequest.Line("SKU-0001", 100)
        ));

        assertThatThrownBy(() -> inboundReceptionService.receive(request))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.DUPLICATE_LINE_PRODUCT);
    }

    @Test
    @DisplayName("상태로 조회하면 실패 사유를 포함해 반환한다")
    void findsByStatus() {
        InboundInterface failed = new InboundInterface("ASN-0003", "{}", LocalDateTime.now());
        failed.fail("미등록 상품: SKU-9999");
        given(inboundInterfaceRepository.findAllByStatusOrderByIdDesc(InboundInterfaceStatus.FAILED)).willReturn(List.of(failed));

        List<InboundInterfaceResponse> responses = inboundReceptionService.findAllByStatus(InboundInterfaceStatus.FAILED);

        assertThat(responses).singleElement()
                .satisfies(response -> {
                    assertThat(response.asnNo()).isEqualTo("ASN-0003");
                    assertThat(response.status()).isEqualTo(InboundInterfaceStatus.FAILED);
                    assertThat(response.failReason()).isEqualTo("미등록 상품: SKU-9999");
                });
    }

    private static InboundExpectationRequest request(List<InboundExpectationRequest.Line> lines) {
        return new InboundExpectationRequest("ASN-0001", "SUP-001", LocalDateTime.of(2026, 9, 22, 6, 0), lines);
    }
}
