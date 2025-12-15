package com.fedex.automation.service.fedex;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.specification.RequestSpecification;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
public class SessionService {

    @Value("${base.url}")
    private String baseUrl;

    @Getter
    private final CookieFilter cookieFilter = new CookieFilter();

    @Getter
    private String formKey;

    private static final Pattern FORM_KEY_INPUT_PATTERN = Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");
    private static final Pattern FORM_KEY_JSON_PATTERN = Pattern.compile("\"formKey\":\"([^\"]+)\"");

    /**
     * Provides a RequestSpecification with the active session cookies.
     */
    public RequestSpecification authenticatedRequest() {
        RequestSpecification spec = given()
                .relaxedHTTPSValidation() // <--- FIX: Added this to ignore SSL errors
                .baseUri(baseUrl)
                .filter(cookieFilter); // Maintains session automatically

        if (formKey != null && !formKey.isBlank()) {
            spec.cookie("form_key", formKey);
        }
        return spec;
    }

    public void bootstrapSession() {
        log.info("--- Initializing Session ---");
        // This call will now use the relaxed HTTPS validation
        String html = authenticatedRequest()
                .get("/default/checkout/cart/")
                .getBody().asString();

        this.formKey = extractFormKey(html);
        if (this.formKey == null) {
            log.warn("Form key not found.");
        } else {
            log.info("Session initialized. Form Key: {}", formKey);
        }
    }

    private String extractFormKey(String html) {
        Matcher inputMatcher = FORM_KEY_INPUT_PATTERN.matcher(html);
        if (inputMatcher.find()) return inputMatcher.group(1);

        Matcher jsonMatcher = FORM_KEY_JSON_PATTERN.matcher(html);
        if (jsonMatcher.find()) return jsonMatcher.group(1);

        return null;
    }
}