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

    public void login(String username, String password) {
        log.info("--- Performing Login for User: {} ---", username);
        if (sessionCookies.isEmpty()) bootstrapSession();

        Map<String, String> authCookies = authenticationService.login(username, password, sessionCookies);
        if (authCookies != null && !authCookies.isEmpty()) {
            this.sessionCookies.putAll(authCookies);
        } else {
            throw new RuntimeException("Login failed for user " + username);
        }
        bootstrapSession();
    }

    /**
     * Highly dynamic core builder to prevent duplicate headers.
     */
    private RequestSpecification buildBaseRequest(Map<String, String> extraCookies, String refererUrl, String originUrl, String secFetchSite) {
        RequestSpecification spec = given()
                .spec(defaultRequestSpec)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0")
                .header("Referer", refererUrl)
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", secFetchSite);

        if (originUrl != null && !originUrl.isEmpty()) {
            spec.header("Origin", originUrl);
        }

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

        if (!cookieBuilder.isEmpty()) {
            String rawCookieHeader = cookieBuilder.toString().trim();
            if (rawCookieHeader.endsWith(";")) rawCookieHeader = rawCookieHeader.substring(0, rawCookieHeader.length() - 1);
            spec.header("Cookie", rawCookieHeader);
        }

        return spec;
    }

    // --- Standard Request (same-origin, staging2) ---
    public RequestSpecification authenticatedRequest() {
        return buildBaseRequest(null, baseUrl + "/", baseUrl, "same-origin").baseUri(baseUrl);
    }

    public RequestSpecification authenticatedRequest(Map<String, String> extraCookies) {
        return buildBaseRequest(extraCookies, baseUrl + "/", baseUrl, "same-origin").baseUri(baseUrl);
    }

    // --- Checkout Request (no Origin, checkout referer) ---
    public RequestSpecification checkoutRequest() {
        return buildBaseRequest(null, baseUrl + "/default/checkout", null, "same-origin").baseUri(baseUrl);
    }

    public RequestSpecification checkoutRequest(Map<String, String> extraCookies) {
        return buildBaseRequest(extraCookies, baseUrl + "/default/checkout", null, "same-origin").baseUri(baseUrl);
    }

    // --- NEW: Configurator Request (Cross-Domain, same-site) ---
    public RequestSpecification configuratorRequest(String originUrl, String refererUrl) {
        return buildBaseRequest(null, refererUrl, originUrl, "same-site");
        // We leave baseUri off here because TemplateConfiguratorService explicitly calls .baseUri(apiGatewayUri)
    }


    public void bootstrapSession() {
        try {
            // Note: Added baseUri(baseUrl) here as well to ensure the bootstrap hits the right environment
            var response = authenticatedRequest().get("/default/checkout/cart/").then().extract().response();
            if (response.getCookies() != null && !response.getCookies().isEmpty()) {
                this.sessionCookies.putAll(response.getCookies());
            }
            Matcher inputMatcher = FORM_KEY_INPUT_PATTERN.matcher(response.getBody().asString());
            if (inputMatcher.find()) this.formKey = inputMatcher.group(1);
        } catch (Exception e) {
            log.warn("Bootstrap request failed: {}", e.getMessage());
        }
    }
}