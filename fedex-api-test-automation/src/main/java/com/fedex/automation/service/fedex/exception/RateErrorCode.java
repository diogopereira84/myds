package com.fedex.automation.service.fedex.exception;

public enum RateErrorCode {
    TEMPLATE_NOT_FOUND,
    TEMPLATE_SCHEMA_ERROR,
    TEMPLATE_PARSE_ERROR,
    INVALID_REQUEST,
    NULL_RESPONSE,
    UPSTREAM_STATUS_ERROR,
    PARSE_ERROR,
    BUSINESS_STATUS_FALSE,
    MISSING_TOTAL_AMOUNT
}
