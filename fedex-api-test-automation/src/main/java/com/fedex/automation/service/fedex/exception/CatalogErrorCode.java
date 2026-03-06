package com.fedex.automation.service.fedex.exception;

public enum CatalogErrorCode {
    INVALID_INPUT,
    STRATEGY_NOT_FOUND,
    NULL_RESPONSE,
    UPSTREAM_STATUS_ERROR,
    UPSTREAM_GRAPHQL_ERROR,
    NO_RESULTS,
    NO_STRATEGY_MATCH,
    PARSE_ERROR
}

