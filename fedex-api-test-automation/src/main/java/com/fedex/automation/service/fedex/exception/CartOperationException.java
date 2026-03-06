package com.fedex.automation.service.fedex.exception;

import lombok.Getter;

@Getter
public class CartOperationException extends RuntimeException {

    private final CartErrorCode errorCode;

    public CartOperationException(CartErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CartOperationException(CartErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

