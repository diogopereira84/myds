package com.fedex.automation.service.fedex.exception;

import lombok.Getter;

@Getter
public class DocumentOperationException extends RuntimeException {

    private final DocumentErrorCode errorCode;

    public DocumentOperationException(DocumentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentOperationException(DocumentErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

