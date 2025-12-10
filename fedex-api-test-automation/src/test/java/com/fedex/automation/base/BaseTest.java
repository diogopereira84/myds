package com.fedex.automation.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.CartContext;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@SpringBootTest
public class BaseTest {

    @Value("${base.url}")
    protected String baseUrl;

    protected final CookieFilter cookieFilter = new CookieFilter();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Magento form_key used for CSRF validation.
     * We extract it from the cart page and also send it as a cookie when needed.
     */
    protected String formKey;

    protected final Filter curlLoggingFilter = new CurlLoggingFilter();

    private static final Pattern FORM_KEY_INPUT_PATTERN =
            Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");
    private static final Pattern FORM_KEY_JSON_PATTERN =
            Pattern.compile("\"formKey\":\"([^\"]+)\"");

    @BeforeEach
    public void setup() {
        RestAssured.baseURI = baseUrl;
        RestAssured.useRelaxedHTTPSValidation();
    }

    /**
     * Base RestAssured spec carrying cookies captured so far.
     */
    protected RequestSpecification givenBase() {
        return given()
                .filter(cookieFilter)
                .filter(curlLoggingFilter);
    }

    /**
     * RestAssured spec that also forces the form_key cookie when available.
     * This helps align the automation with the browser request behavior.
     */
    protected RequestSpecification givenWithSession() {
        RequestSpecification spec = givenBase();
        if (formKey != null && !formKey.isBlank()) {
            spec.cookie("form_key", formKey);
        }
        return spec;
    }

    /**
     * Initializes session cookies and extracts form_key from the cart page.
     */
    protected void bootstrapSession() {
        log.info("--- [Step 0] Initializing Session (Bootstrap) ---");

        Response response = givenBase()
                .get("/default/checkout/cart/");

        String html = response.getBody().asString();

        Matcher inputMatcher = FORM_KEY_INPUT_PATTERN.matcher(html);
        if (inputMatcher.find()) {
            this.formKey = inputMatcher.group(1);
            log.info("Form key extracted from hidden input.");
            return;
        }

        Matcher jsonMatcher = FORM_KEY_JSON_PATTERN.matcher(html);
        if (jsonMatcher.find()) {
            this.formKey = jsonMatcher.group(1);
            log.info("Form key extracted from JSON config.");
            return;
        }

        log.warn("Form key was not found on the cart page response.");
    }

    /**
     * Reads the cart page and extracts cart context for the target SKU.
     * This method expects bootstrapSession() to have been called.
     */
    protected CartContext scrapeCartContext(String targetSku) {
        log.info("--- Extracting Cart data for SKU: {} ---", targetSku);

        Response response = givenWithSession()
                .get("/default/checkout/cart/");

        String html = response.getBody().asString();
        String jsonString = extractJsonConfig(html);

        if (jsonString == null) {
            fail("Could not find window.checkoutConfig on the cart page.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode quoteItemData = root.path("quoteItemData");

            String maskedQuoteId = root.path("quoteData").path("entity_id").asText();
            String realQuoteId = (!quoteItemData.isEmpty())
                    ? quoteItemData.get(0).path("quote_id").asText()
                    : "";

            String itemId = null;
            int qty = 0;

            if (quoteItemData.isArray()) {
                for (JsonNode item : quoteItemData) {
                    if (targetSku.equals(item.path("sku").asText())) {
                        itemId = item.path("item_id").asText();
                        qty = item.path("qty").asInt();
                        break;
                    }
                }
            }

            if (itemId == null) {
                fail("Item " + targetSku + " not found in the cart JSON.");
            }

            return CartContext.builder()
                    .formKey(this.formKey)
                    .quoteId(realQuoteId)
                    .maskedQuoteId(maskedQuoteId)
                    .itemId(itemId)
                    .qty(qty)
                    .build();

        } catch (Exception e) {
            fail("Failed to parse Cart JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts window.checkoutConfig JSON content from the cart HTML.
     */
    private String extractJsonConfig(String html) {
        String marker = "window.checkoutConfig =";
        int startIndex = html.indexOf(marker);
        if (startIndex == -1) return null;

        int openBraceIndex = html.indexOf("{", startIndex);
        if (openBraceIndex == -1) return null;

        int braceCount = 0;

        for (int i = openBraceIndex; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return html.substring(openBraceIndex, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Fetches the payment encryption public key from the server.
     */
    protected String fetchEncryptionKey() {
        log.info("--- Fetching Encryption Key from API ---");

        String endpoint = "/default/delivery/index/encryptionkey";
        Response response = givenWithSession()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .get(endpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        String key = response.jsonPath().getString("encryption.key");
        if (key == null || key.isBlank()) {
            throw new RuntimeException("Encryption key not found in API response: " + response.asString());
        }

        log.info("Fetched public key successfully.");
        return key;
    }

    /**
     * Debug-only filter to print a runnable curl reproduction command.
     */
    public static class CurlLoggingFilter implements Filter {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Response filter(FilterableRequestSpecification requestSpec,
                               FilterableResponseSpecification responseSpec,
                               FilterContext ctx) {

            StringBuilder curl = new StringBuilder("\n\nCURL REPRODUCTION COMMAND:\n");
            curl.append("curl -v -X ").append(requestSpec.getMethod()).append(" \\\n");
            curl.append("  '").append(requestSpec.getURI()).append("' \\\n");

            for (Header header : requestSpec.getHeaders()) {
                if ("Cookie".equalsIgnoreCase(header.getName())) {
                    continue;
                }
                curl.append("  -H '")
                        .append(header.getName())
                        .append(": ")
                        .append(header.getValue())
                        .append("' \\\n");
            }

            var cookies = requestSpec.getCookies();
            if (cookies != null && !cookies.asList().isEmpty()) {
                curl.append("  -b '");
                for (Cookie cookie : cookies.asList()) {
                    curl.append(cookie.getName())
                            .append("=")
                            .append(cookie.getValue())
                            .append("; ");
                }
                curl.setLength(curl.length() - 2);
                curl.append("' \\\n");
            }

            if (requestSpec.getBody() != null) {
                try {
                    String bodyStr = (requestSpec.getBody() instanceof String)
                            ? (String) requestSpec.getBody()
                            : mapper.writeValueAsString(requestSpec.getBody());

                    curl.append("  --data-raw '")
                            .append(escapeSingleQuotes(bodyStr))
                            .append("'");
                } catch (Exception e) {
                    curl.append("  # [Error serializing body]");
                }
            } else if (requestSpec.getFormParams() != null && !requestSpec.getFormParams().isEmpty()) {

                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, ?> entry : requestSpec.getFormParams().entrySet()) {
                    appendFormParamPairs(sb, entry.getKey(), entry.getValue());
                }

                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '&') {
                    sb.setLength(sb.length() - 1);
                }

                curl.append("  --data-raw '")
                        .append(escapeSingleQuotes(sb.toString()))
                        .append("'");
            }

            log.debug(curl.toString());
            return ctx.next(requestSpec, responseSpec);
        }

        /**
         * Expands multi-values (Iterable/array) to repeat the key.
         * Example: street[]=A & street[]=
         */
        private static void appendFormParamPairs(StringBuilder sb, String key, Object value) {
            if (value == null) {
                sb.append(key).append("=&");
                return;
            }

            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    sb.append(key).append("=")
                            .append(String.valueOf(item))
                            .append("&");
                }
                return;
            }

            if (value.getClass().isArray()) {
                int len = Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    Object item = Array.get(value, i);
                    sb.append(key).append("=")
                            .append(String.valueOf(item))
                            .append("&");
                }
                return;
            }

            sb.append(key).append("=")
                    .append(String.valueOf(value))
                    .append("&");
        }

        private static String escapeSingleQuotes(String s) {
            return s == null ? "" : s.replace("'", "'\\''");
        }
    }
}
