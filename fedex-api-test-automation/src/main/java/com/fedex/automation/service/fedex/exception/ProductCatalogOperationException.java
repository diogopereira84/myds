package com.fedex.automation.service.fedex.exception;

import lombok.Getter;

@Getter
public class ProductCatalogOperationException extends RuntimeException {

    private final ProductCatalogErrorCode errorCode;

    public ProductCatalogOperationException(ProductCatalogErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ProductCatalogOperationException(ProductCatalogErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

