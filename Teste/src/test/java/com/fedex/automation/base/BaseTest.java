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
        Matcher m = Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"").matcher(html);
        if (m.find()) {
            this.formKey = m.group(1);
        } else {
            Matcher m2 = Pattern.compile("\"formKey\":\"([^\"]+)\"").matcher(html);
            if(m2.find()) this.formKey = m2.group(1);
        }
    }

    protected CartContext scrapeCartContext(String targetSku) {
        log.info("--- Extracting Cart data for SKU: {} ---", targetSku);
        Response response = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .get("/default/checkout/cart/");

        String html = response.getBody().asString();
        String jsonString = extractJsonConfig(html);

        if (jsonString == null) fail("Could not find window.checkoutConfig on the cart page.");

        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode quoteItemData = root.path("quoteItemData");
            String quoteId = root.path("quoteData").path("entity_id").asText();
            String realQuoteId = (!quoteItemData.isEmpty()) ? quoteItemData.get(0).path("quote_id").asText() : "";

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
            if (itemId == null) fail("Item " + targetSku + " not found in the cart JSON.");

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
            if (html.charAt(i) == '{') braceCount++;
            else if (html.charAt(i) == '}') {
                braceCount--;
                if (braceCount == 0) return html.substring(openBraceIndex, i + 1);
            }
        }
        return null;
    }

    public static class CurlLoggingFilter implements Filter {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
            StringBuilder curl = new StringBuilder("\n\n📍 CURL REPRODUCTION COMMAND:\n");
            curl.append("curl -v -X ").append(requestSpec.getMethod()).append(" \\\n");
            curl.append("  '").append(requestSpec.getURI()).append("' \\\n");

            for (Header header : requestSpec.getHeaders()) {
                curl.append("  -H '").append(header.getName()).append(": ").append(header.getValue()).append("' \\\n");
            }

            if (requestSpec.getCookies().size() > 0) {
                curl.append("  -b '");
                for (Cookie cookie : requestSpec.getCookies()) {
                    curl.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
                }
                curl.append("' \\\n");
            }

            // MODIFIED: Handle Body and Form Params uniformly using --data-raw for DevTools similarity
            if (requestSpec.getBody() != null) {
                try {
                    String bodyStr = (requestSpec.getBody() instanceof String)
                            ? (String) requestSpec.getBody()
                            : mapper.writeValueAsString(requestSpec.getBody());
                    curl.append("  --data-raw '").append(bodyStr.replace("'", "'\\''")).append("'");
                } catch (Exception e) {
                    curl.append("  # [Error serializing body]");
                }
            } else if (requestSpec.getFormParams() != null && !requestSpec.getFormParams().isEmpty()) {
                // Construct a single string like "key1=value1&key2=value2" to match DevTools style
                StringBuilder sb = new StringBuilder();
                for (var entry : requestSpec.getFormParams().entrySet()) {
                    if (sb.length() > 0) sb.append("&");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
                curl.append("  --data-raw '").append(sb.toString().replace("'", "'\\''")).append("'");
            }

            log.info(curl.toString());
            return ctx.next(requestSpec, responseSpec);
        }
    }
}