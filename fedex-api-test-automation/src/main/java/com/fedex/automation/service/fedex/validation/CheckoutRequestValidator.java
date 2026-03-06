package com.fedex.automation.service.fedex.validation;

import com.fedex.automation.model.fedex.DeliveryRateRequestForm;
import com.fedex.automation.service.fedex.exception.CheckoutErrorCode;
import com.fedex.automation.service.fedex.exception.CheckoutOperationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CheckoutRequestValidator {

    public void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CheckoutOperationException(CheckoutErrorCode.INVALID_REQUEST, message);
        }
    }

    public void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new CheckoutOperationException(CheckoutErrorCode.INVALID_REQUEST, message);
        }
    }

    public List<String> requireStreetLines(DeliveryRateRequestForm form) {
        requireNonNull(form, "DeliveryRateRequestForm cannot be null.");
        List<String> streetLines = form.getStreet();

        if (streetLines == null || streetLines.isEmpty()) {
            throw new CheckoutOperationException(
                    CheckoutErrorCode.INVALID_REQUEST,
                    "DeliveryRateRequestForm.street must contain at least one non-blank line."
            );
        }

        String firstLine = streetLines.get(0);
        if (firstLine == null || firstLine.isBlank()) {
            throw new CheckoutOperationException(
                    CheckoutErrorCode.INVALID_REQUEST,
                    "DeliveryRateRequestForm.street must contain at least one non-blank line."
            );
        }

        return streetLines;
    }
}

