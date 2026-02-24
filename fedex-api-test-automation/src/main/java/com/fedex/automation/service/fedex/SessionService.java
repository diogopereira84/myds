package com.fedex.automation.service.fedex;

import io.restassured.specification.RequestSpecification;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
public class SessionService {

    @Value("${base.url}")
    @Getter
    private String baseUrl;

    @Autowired
    private RequestSpecification defaultRequestSpec;

    @Autowired
    private AuthenticationService authenticationService;

    private final Map<String, String> sessionCookies = new HashMap<>();

    @Getter
    private String formKey;

    private static final Pattern FORM_KEY_INPUT_PATTERN = Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");
    private static final Pattern FORM_KEY_JSON_PATTERN = Pattern.compile("\"formKey\":\"([^\"]+)\"");

    public void login(String username, String password) {
        log.info("--- Performing Login for User: {} ---", username);

        if (sessionCookies.isEmpty()) {
            bootstrapSession();
        }

        Map<String, String> authCookies = authenticationService.login(username, password, sessionCookies);

        if (authCookies != null && !authCookies.isEmpty()) {
            this.sessionCookies.putAll(authCookies);
            log.info("Login successful. Cookies updated.");
        } else {
            log.error("Login failed or no cookies returned.");
            throw new RuntimeException("Login failed for user " + username);
        }

        bootstrapSession();
    }

    // Default method for standard requests
    public RequestSpecification authenticatedRequest() {
        return authenticatedRequest(null);
    }

    // Overloaded method to merge specific per-request cookies (like quoteId) securely
    public RequestSpecification authenticatedRequest(Map<String, String> extraCookies) {
        RequestSpecification spec = given()
                .spec(defaultRequestSpec)
                .baseUri(baseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0")
                .header("Origin", baseUrl)
                .header("Referer", baseUrl + "/")
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin");

        // Merge all cookies into a single valid Cookie header to prevent duplicate header drops
        StringBuilder cookieBuilder = new StringBuilder();

        for (Map.Entry<String, String> entry : sessionCookies.entrySet()) {
            cookieBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("; ");
        }

        if (extraCookies != null) {
            for (Map.Entry<String, String> entry : extraCookies.entrySet()) {
                cookieBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("; ");
            }
        }

        if (formKey != null && !formKey.isBlank()) {
            cookieBuilder.append("form_key=").append(formKey).append("; ");
        }

        if (cookieBuilder.length() > 0) {
            String rawCookieHeader = cookieBuilder.toString().trim();
            if (rawCookieHeader.endsWith(";")) {
                rawCookieHeader = rawCookieHeader.substring(0, rawCookieHeader.length() - 1);
            }
            spec.header("Cookie", rawCookieHeader);
        }

        return spec;
    }

    public void bootstrapSession() {
        log.info("--- Bootstrapping Session ---");
        try {
            var response = authenticatedRequest()
                    .get("/default/checkout/cart/")
                    .then()
                    .extract()
                    .response();

            if (response.getCookies() != null && !response.getCookies().isEmpty()) {
                this.sessionCookies.putAll(response.getCookies());
            }

            String html = response.getBody().asString();
            this.formKey = extractFormKey(html);

            if (this.formKey != null) {
                log.info("Session bootstrapped. Form Key: {}", formKey);
            } else {
                log.warn("Form key not found. (Possible WAF block or Manual Cookies needed)");
            }
        } catch (Exception e) {
            log.warn("Bootstrap request failed: {}", e.getMessage());
        }
    }

    private String extractFormKey(String html) {
        if (html == null) return null;
        Matcher inputMatcher = FORM_KEY_INPUT_PATTERN.matcher(html);
        if (inputMatcher.find()) return inputMatcher.group(1);

        Matcher jsonMatcher = FORM_KEY_JSON_PATTERN.matcher(html);
        if (jsonMatcher.find()) return jsonMatcher.group(1);

        return null;
    }
}