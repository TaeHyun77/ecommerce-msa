package com.park.ecommerce.inbound.dto;

import com.park.ecommerce.inbound.Inbound;
import com.park.ecommerce.inbound.InboundLine;
import com.park.ecommerce.inbound.InboundStatus;
import com.park.ecommerce.inbound.InboundLineStatus;

import java.util.List;

public record InboundResponse(
        String asnNo,
        InboundStatus status,
        long receivedCount,
        List<FailedLine> failedLines
) {
    public record FailedLine(String productCode, String reason) {
    }

    public static InboundResponse from(Inbound inbound) {
        List<FailedLine> failedLines = inbound.getLines().stream()
                .filter(line -> line.getStatus() == InboundLineStatus.FAILED)
                .map(line -> new FailedLine(line.getProductCode(), line.getFailReason()))
                .toList();

        return new InboundResponse(
                inbound.getAsnNo(),
                inbound.getStatus(),
                inbound.getLines().stream().filter(InboundLine::isReceived).count(),
                failedLines
        );
    }
}
