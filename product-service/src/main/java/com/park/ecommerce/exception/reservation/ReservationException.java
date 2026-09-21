package com.park.ecommerce.exception.reservation;

import lombok.Getter;

@Getter
public class ReservationException extends RuntimeException {
    private final ReservationErrorCode errorCode;

    public ReservationException(ReservationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
