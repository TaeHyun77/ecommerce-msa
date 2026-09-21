package com.park.ecommerce.exception.inbound;

import lombok.Getter;

@Getter
public class InboundException extends RuntimeException {
    private final InboundErrorCode errorCode;

    public InboundException(InboundErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
