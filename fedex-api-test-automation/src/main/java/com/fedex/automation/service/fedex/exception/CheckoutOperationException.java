package com.fedex.automation.service.fedex.exception;

import lombok.Getter;

@Getter
public class CheckoutOperationException extends RuntimeException {

    private final CheckoutErrorCode errorCode;

    public CheckoutOperationException(CheckoutErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CheckoutOperationException(CheckoutErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

