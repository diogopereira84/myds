package com.fedex.automation.service.fedex;

import com.fedex.automation.config.FedexConfig;
import com.fedex.automation.service.fedex.parser.FormKeyExtractor;
import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@ScenarioScope
public class SessionService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0";
    private static final String COOKIE_FORM_KEY = "form_key";
    private static final String COOKIE_PHPSESSID = "PHPSESSID";
    private static final String CHECKOUT_CART_PATH = "/default/checkout/cart/";
    private static final String CHECKOUT_REFERER_PATH = "/default/checkout";

    private final RequestSpecification defaultRequestSpec;
    private final FedexConfig fedexConfig;
    private final FormKeyExtractor formKeyExtractor;

    private final Map<String, String> sessionCookies = new HashMap<>();

    @Getter
    private String formKey;

    public SessionService(
            RequestSpecification defaultRequestSpec,
            FedexConfig fedexConfig,
            FormKeyExtractor formKeyExtractor
    ) {
        this.defaultRequestSpec = defaultRequestSpec;
        this.fedexConfig = fedexConfig;
        this.formKeyExtractor = formKeyExtractor;
    }

    /**
     * ABSOLUTE STATE CLEAR: Call this at the start of every test case
     * to guarantee 100% isolation and no cookie/formKey reuse.
     */
    public void clearSession() {
        log.info("--- [Scenario Setup] Wiping previous Session State (Cookies & FormKey) ---");
        sessionCookies.clear();
        formKey = null;
    }

    /**
     * Highly dynamic core builder to prevent duplicate headers.
     */
    private RequestSpecification buildRequest(RequestContext context, Map<String, String> extraCookies) {
        String baseUrl = getBaseUrl();

        RequestSpecification spec = given()
                .spec(defaultRequestSpec)
                .baseUri(baseUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", context.referer)
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", context.secFetchSite);

        if (context.origin != null && !context.origin.isBlank()) {
            spec.header("Origin", context.origin);
        }

        String cookieHeader = buildCookieHeader(extraCookies);
        if (!cookieHeader.isBlank()) {
            spec.header("Cookie", cookieHeader);
        }

        return spec;
    }

    // --- Standard Request (same-origin, staging2) ---
    public RequestSpecification authenticatedRequest() {
        ensureSessionReady();
        return buildRequest(RequestContext.authenticated(getBaseUrl()), null);
    }

    public RequestSpecification authenticatedRequest(Map<String, String> extraCookies) {
        ensureSessionReady();
        return buildRequest(RequestContext.authenticated(getBaseUrl()), extraCookies);
    }

    // --- Checkout Request (no Origin, checkout referer) ---
    public RequestSpecification checkoutRequest() {
        ensureSessionReady();
        return buildRequest(RequestContext.checkout(getBaseUrl()), null);
    }

    public RequestSpecification checkoutRequest(Map<String, String> extraCookies) {
        ensureSessionReady();
        return buildRequest(RequestContext.checkout(getBaseUrl()), extraCookies);
    }

    // --- NEW: Configurator Request (Cross-Domain, same-site) ---
    public RequestSpecification configuratorRequest(String originUrl, String refererUrl) {
        return buildRequest(RequestContext.configurator(originUrl, refererUrl), null);
    }

    public boolean bootstrapSession() {
        String baseUrl = getBaseUrl();
        Response response;

        try {
            response = fetchBootstrapResponse(baseUrl);
        } catch (Exception e) {
            log.warn("Bootstrap request failed for baseUrl={}: {}", baseUrl, e.toString());
            return false;
        }

        if (response == null) {
            log.warn("Bootstrap request returned null response for baseUrl={}", baseUrl);
            return false;
        }

        if (response.getStatusCode() >= 400) {
            log.warn("Bootstrap request failed with status={} for baseUrl={}", response.getStatusCode(), baseUrl);
            return false;
        }

        updateSessionCookies(response.getCookies());
        formKey = formKeyExtractor.extract(response.getBody().asString());

        if (formKey != null && log.isDebugEnabled()) {
            log.debug("Extracted form_key (masked): {}", maskSecret(formKey));
        }
        return formKey != null && !formKey.isBlank();
    }

    private Response fetchBootstrapResponse(String baseUrl) {
        return buildRequest(RequestContext.authenticated(baseUrl), null)
                .baseUri(baseUrl)
                .get(CHECKOUT_CART_PATH)
                .then()
                .extract()
                .response();
    }

    private void ensureSessionReady() {
        if ((formKey == null || formKey.isBlank()) && !bootstrapSession()) {
            throw new IllegalStateException("Session is not ready: missing form_key after bootstrap.");
        }
    }

    private String buildCookieHeader(Map<String, String> extraCookies) {
        Map<String, String> combinedCookies = new TreeMap<>(sessionCookies);

        if (extraCookies != null && !extraCookies.isEmpty()) {
            combinedCookies.putAll(extraCookies);
        }

        if (formKey != null && !formKey.isBlank() && !combinedCookies.containsKey(COOKIE_FORM_KEY)) {
            combinedCookies.put(COOKIE_FORM_KEY, formKey);
        }

        if (combinedCookies.isEmpty()) {
            return "";
        }

        StringBuilder cookieBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : combinedCookies.entrySet()) {
            cookieBuilder.append(entry.getKey()).append('=').append(entry.getValue()).append("; ");
        }

        String rawCookieHeader = cookieBuilder.toString().trim();
        return rawCookieHeader.endsWith(";")
                ? rawCookieHeader.substring(0, rawCookieHeader.length() - 1)
                : rawCookieHeader;
    }

    private void updateSessionCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return;
        }

        sessionCookies.putAll(cookies);

        if (sessionCookies.containsKey(COOKIE_PHPSESSID) && log.isDebugEnabled()) {
            log.debug("Extracted PHPSESSID (masked): {}", maskSecret(sessionCookies.get(COOKIE_PHPSESSID)));
        }
    }


    private String maskSecret(String value) {
        if (value == null) {
            return "null";
        }

        int visibleChars = 4;
        if (value.length() <= visibleChars) {
            return "****";
        }
        return "****" + value.substring(value.length() - visibleChars);
    }

    public String getBaseUrl() {
        return fedexConfig.getBaseUrl();
    }

    private static final class RequestContext {
        private final String secFetchSite;
        private final String referer;
        private final String origin;

        private RequestContext(String secFetchSite, String referer, String origin) {
            this.secFetchSite = secFetchSite;
            this.referer = referer;
            this.origin = origin;
        }

        private static RequestContext authenticated(String baseUrl) {
            return new RequestContext("same-origin", baseUrl + "/", baseUrl);
        }

        private static RequestContext checkout(String baseUrl) {
            return new RequestContext("same-origin", baseUrl + CHECKOUT_REFERER_PATH, null);
        }

        private static RequestContext configurator(String originUrl, String refererUrl) {
            return new RequestContext("same-site", refererUrl, originUrl);
        }
    }
}