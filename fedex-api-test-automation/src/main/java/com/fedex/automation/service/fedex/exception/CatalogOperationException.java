package com.fedex.automation.service.fedex.exception;

import lombok.Getter;

@Getter
public class CatalogOperationException extends RuntimeException {

    private final CatalogErrorCode errorCode;

    public CatalogOperationException(CatalogErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CatalogOperationException(CatalogErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

