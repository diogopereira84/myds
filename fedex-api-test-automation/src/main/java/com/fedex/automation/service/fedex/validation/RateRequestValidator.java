package com.fedex.automation.service.fedex.validation;

import com.fedex.automation.service.fedex.exception.RateErrorCode;
import com.fedex.automation.service.fedex.exception.RateOperationException;
import org.springframework.stereotype.Component;

@Component
public class RateRequestValidator {

    public void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new RateOperationException(RateErrorCode.INVALID_REQUEST, message);
        }
    }
}

