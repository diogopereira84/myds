package com.fedex.automation.service.fedex.parser;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FormKeyExtractor {

    private static final Pattern FORM_KEY_INPUT_PATTERN = Pattern.compile(
            "<input[^>]*name=\"form_key\"[^>]*value=\"([^\"]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    public String extract(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        Matcher matcher = FORM_KEY_INPUT_PATTERN.matcher(responseBody);
        return matcher.find() ? matcher.group(1) : null;
    }
}

