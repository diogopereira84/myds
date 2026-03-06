package com.fedex.automation.service.fedex.exception;

public enum CheckoutErrorCode {
    INVALID_REQUEST,
    SERIALIZATION_ERROR,
    NULL_RESPONSE,
    UPSTREAM_STATUS_ERROR,
    BUSINESS_RULE_VIOLATION,
    PARSE_ERROR,
    MISSING_ENCRYPTION_KEY
}

