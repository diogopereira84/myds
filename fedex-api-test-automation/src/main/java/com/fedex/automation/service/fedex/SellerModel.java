package com.fedex.automation.service.fedex;

import java.util.Locale;

public enum SellerModel {
    ONE_P("1P"),
    THREE_P("3P");

    private final String externalValue;

    SellerModel(String externalValue) {
        this.externalValue = externalValue;
    }

    public String externalValue() {
        return externalValue;
    }

    public static SellerModel fromNullableInput(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return THREE_P;
        }

        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "1P" -> ONE_P;
            case "3P" -> THREE_P;
            default -> throw new IllegalArgumentException("Unsupported sellerModel: " + rawValue);
        };
    }
}

