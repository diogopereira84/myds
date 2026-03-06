package com.fedex.automation.service.fedex.exception;

import lombok.Getter;

@Getter
public class RateOperationException extends RuntimeException {

    private final RateErrorCode errorCode;

    public RateOperationException(RateErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RateOperationException(RateErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

