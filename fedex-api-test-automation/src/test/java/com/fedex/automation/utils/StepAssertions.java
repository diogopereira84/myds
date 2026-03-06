package com.fedex.automation.utils;

import io.cucumber.datatable.DataTable;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;

public final class StepAssertions {

    private StepAssertions() {
    }

    public static void assertTableNotEmpty(DataTable table, String message) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        if (rows.isEmpty()) {
            Assertions.fail(message);
        }
    }

    public static void requireKeys(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value == null || value.isBlank()) {
                Assertions.fail("Missing required column: " + key);
            }
        }
    }

    public static int parsePositiveInt(String value, String fieldName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                Assertions.fail(fieldName + " must be greater than zero: " + value);
            }
            return parsed;
        } catch (Exception e) {
            Assertions.fail("Invalid " + fieldName + ": " + value);
            return 0;
        }
    }
}

