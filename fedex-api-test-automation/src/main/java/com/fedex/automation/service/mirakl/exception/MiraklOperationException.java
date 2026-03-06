package com.fedex.automation.service.mirakl.exception;

import lombok.Getter;

@Getter
public class MiraklOperationException extends RuntimeException {

    private final MiraklErrorCode errorCode;

    public MiraklOperationException(MiraklErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MiraklOperationException(MiraklErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

