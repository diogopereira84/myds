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
    protected String formKey;

    private static final Pattern FORM_KEY_INPUT_PATTERN =
            Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");
    private static final Pattern FORM_KEY_JSON_PATTERN =
            Pattern.compile("\"formKey\":\"([^\"]+)\"");

    @BeforeEach
    public void setup() {
        RestAssured.baseURI = baseUrl;
        RestAssured.useRelaxedHTTPSValidation();
    }

    protected void bootstrapSession() {
        log.info("--- [Step 0] Initializing Session (Bootstrap) ---");

        Response response = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .get("/default/checkout/cart/");

        String html = response.getBody().asString();

        Matcher m = FORM_KEY_INPUT_PATTERN.matcher(html);
        if (m.find()) {
            this.formKey = m.group(1);
            return;
        }

        Matcher m2 = FORM_KEY_JSON_PATTERN.matcher(html);
        if (m2.find()) {
            this.formKey = m2.group(1);
        }
    }

    protected String fetchEncryptionKey() {
        log.info("--- Fetching Encryption Key from API ---");

        // The JS calls "delivery/index/encryptionkey" via urlBuilder
        // Assuming your baseUrl is the domain, we prepend /default/
        String endpoint = "/default/delivery/index/encryptionkey";

        Response response = given()
                .filter(cookieFilter) // Uses the session cookies
                .filter(new CurlLoggingFilter())
                .header("X-Requested-With", "XMLHttpRequest") // Good practice for Magento AJAX calls
                .get(endpoint)
                .then()
                .statusCode(200)
                .extract().response();

        // The JS accesses it via: data.encryption.key
        String key = response.jsonPath().getString("encryption.key");

        if (key == null || key.isEmpty()) {
            throw new RuntimeException("Encryption key not found in API response: " + response.asString());
        }

        log.info("Fetched Public Key successfully");
        return key;
    }

    protected String getPublicKeyFromCheckoutConfig() {
        Response response = given()
                .filter(cookieFilter)
                .get("/default/checkout/");

        String html = response.getBody().asString();

        // DEBUG: Print the response to see if we are on the right page
        if (!html.contains("window.checkoutConfig")) {
            log.error("'window.checkoutConfig' NOT FOUND. Current URL might be a redirect.");
            log.info("Response HTML Snippet: " + html.substring(0, Math.min(html.length(), 500)));
        } else {
            log.info("Found checkoutConfig object.");
        }

        // Try a more flexible regex (handles spaces/quotes)
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[\"']cc_encryption_key[\"']\\s*:\\s*[\"']([^\"']+)[\"']");
        java.util.regex.Matcher m = p.matcher(html);

        if (m.find()) {
            String key = m.group(1).replace("\\n", "");
            log.info("Found Public Key: " + key.substring(0, 20) + "...");
            return key;
        }

        // Fail hard if not found
        throw new RuntimeException("Could not find cc_encryption_key in HTML. Check logs for response content.");
    }

    protected CartContext scrapeCartContext(String targetSku) {
        log.info("--- Extracting Cart data for SKU: {} ---", targetSku);

        Response response = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .get("/default/checkout/cart/");

        String html = response.getBody().asString();
        String jsonString = extractJsonConfig(html);

        if (jsonString == null) {
            fail("Could not find window.checkoutConfig on the cart page.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode quoteItemData = root.path("quoteItemData");
            String quoteId = root.path("quoteData").path("entity_id").asText();
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
                    .maskedQuoteId(quoteId)
                    .itemId(itemId)
                    .qty(qty)
                    .build();

        } catch (Exception e) {
            fail("Failed to parse Cart JSON: " + e.getMessage());
            return null;
        }
    }

    private String extractJsonConfig(String html) {
        String marker = "window.checkoutConfig =";
        int startIndex = html.indexOf(marker);
        if (startIndex == -1) return null;

        int openBraceIndex = html.indexOf("{", startIndex);
        if (openBraceIndex == -1) return null;

        int braceCount = 0;

        for (int i = openBraceIndex; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return html.substring(openBraceIndex, i + 1);
                }
            }
        }
        return null;
    }

    public static class CurlLoggingFilter implements Filter {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Response filter(FilterableRequestSpecification requestSpec,
                               FilterableResponseSpecification responseSpec,
                               FilterContext ctx) {

            StringBuilder curl = new StringBuilder("\n\nCURL REPRODUCTION COMMAND:\n");
            curl.append("curl -v -X ").append(requestSpec.getMethod()).append(" \\\n");
            curl.append("  '").append(requestSpec.getURI()).append("' \\\n");

            // Headers (evita duplicar Cookie, já que imprimimos -b)
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

            // Cookies
            var cookies = requestSpec.getCookies();
            if (cookies != null && !cookies.asList().isEmpty()) {
                curl.append("  -b '");
                for (Cookie cookie : cookies.asList()) {
                    curl.append(cookie.getName()).append("=")
                            .append(cookie.getValue()).append("; ");
                }
                curl.setLength(curl.length() - 2);
                curl.append("' \\\n");
            }

            // Body tem prioridade
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
            }
            // Form params no estilo DevTools: key=value&key=value
            else if (requestSpec.getFormParams() != null && !requestSpec.getFormParams().isEmpty()) {

                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, ?> entry : requestSpec.getFormParams().entrySet()) {
                    appendFormParamPairs(sb, entry.getKey(), entry.getValue());
                }

                // remove último &
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
         * Expande multi-values (Iterable/array) para repetir a chave.
         * Ex.: street[]=A & street[]=
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
