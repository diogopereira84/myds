package com.fedex.automation.service.fedex.exception;

import lombok.Getter;

@Getter
public class ConfiguratorOperationException extends RuntimeException {

    private final ConfiguratorErrorCode errorCode;

    public ConfiguratorOperationException(ConfiguratorErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ConfiguratorOperationException(ConfiguratorErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

