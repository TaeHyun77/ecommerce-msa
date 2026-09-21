package com.park.ecommerce.inbound.reception;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 입고 예정서 수신 - 형식만 검증해 원본을 저장하고, 상품 존재 여부 등은 스케줄러가 비동기로 검증
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundReceptionService {
    private final InboundInterfaceRepository inboundInterfaceRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public void receive(InboundExpectationRequest request) {
        if (request.hasDuplicateProduct()) {
            throw new InboundException(InboundErrorCode.DUPLICATE_LINE_PRODUCT);
        }
        String payload = jsonMapper.writeValueAsString(request);
        Optional<InboundInterface> existing = inboundInterfaceRepository.findByAsnNo(request.asnNo());

        if (existing.isEmpty()) {
            inboundInterfaceRepository.save(new InboundInterface(request.asnNo(), payload, LocalDateTime.now()));
            log.info("[입고 수신] 예정서 접수 asnNo={}, 품목 수={}", request.asnNo(), request.lines().size());
            return;
        }

        InboundInterface inboundInterface = existing.get();
        // 실패한 예정서는 공급사의 정정본으로 보고 원본을 교체해 다시 처리
        if (inboundInterface.isFailed()) {
            inboundInterface.resubmit(payload, LocalDateTime.now());
            log.info("[입고 수신] 실패한 예정서 정정 재수신 asnNo={}, 이전 실패 사유={}", request.asnNo(), inboundInterface.getFailReason());
            return;
        }
        // 처리 대기·완료 건은 공급사가 응답을 못 받고 재전송한 것으로 보고 무시 - 처리 중이거나 이미 입고 예정이 만들어진 건의 수정은 범위 밖
        log.info("[입고 수신] 이미 접수된 예정서 재수신 무시 asnNo={}, 상태={}", request.asnNo(), inboundInterface.getStatus());
    }

    // 원인(상품 미등록 등)을 해결한 담당자가 실패 건을 다시 처리 대상으로 돌린다
    @Transactional
    public void retry(String asnNo) {
        InboundInterface inboundInterface = inboundInterfaceRepository.findByAsnNo(asnNo)
                .orElseThrow(() -> new InboundException(InboundErrorCode.INTERFACE_NOT_FOUND));

        inboundInterface.retryManually(LocalDateTime.now());
        log.info("[입고 수신] 실패한 예정서 수동 재처리 asnNo={}, 이전 실패 사유={}", asnNo, inboundInterface.getFailReason());
    }

    // FAILED는 사람이 조치할 대상이라 건수가 적다고 보고 페이징 없이 최신순 전체를 반환
    @Transactional(readOnly = true)
    public List<InboundInterfaceResponse> findAllByStatus(InboundInterfaceStatus status) {
        return inboundInterfaceRepository.findAllByStatusOrderByIdDesc(status).stream()
                .map(InboundInterfaceResponse::from)
                .toList();
    }
}
