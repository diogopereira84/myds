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

        // 2. Normal Flow
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

    private void parseManualCookies(String cookieString) {
        String[] parts = cookieString.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                this.sessionCookies.put(kv[0].trim(), kv[1].trim());
            }
        }
    }

    public RequestSpecification authenticatedRequest() {
        RequestSpecification spec = given()
                .spec(defaultRequestSpec)
                .baseUri(baseUrl);

        if (!sessionCookies.isEmpty()) {
            spec.cookies(sessionCookies);
        }

        if (formKey != null && !formKey.isBlank()) {
            spec.cookie("form_key", formKey);
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

            if (response.getCookies() != null) {
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