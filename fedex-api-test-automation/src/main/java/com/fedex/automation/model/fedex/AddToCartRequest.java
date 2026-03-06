package com.fedex.automation.model.fedex;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class AddToCartRequest {
    private final String formKey;
    private final String sku;
    private final int quantity;
    private final String offerId;
    private final String punchoutDisabled;
    private final String superAttribute;

    @Builder
    private AddToCartRequest(
            String formKey,
            String sku,
            int quantity,
            String offerId,
            String punchoutDisabled,
            String superAttribute
    ) {
        this.formKey = requireNonBlank(formKey, "formKey");
        this.sku = requireNonBlank(sku, "sku");
        this.quantity = requirePositive(quantity);
        this.offerId = requireNonBlank(offerId, "offerId");
        this.punchoutDisabled = isBlank(punchoutDisabled) ? "1" : punchoutDisabled;
        this.superAttribute = superAttribute == null ? "" : superAttribute;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value.trim();
    }

    private static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("quantity" + " must be greater than 0.");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
