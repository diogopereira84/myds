package com.fedex.automation.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.CartContext;
import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * BaseTest handles the infrastructure:
 * 1. Loads the Spring Configuration
 * 2. Defines RestAssured standards (default settings)
 * 3. Generates the Session (Cookies)
 * 4. Scrapes the FormKey and Cart data
 */
@Slf4j
@SpringBootTest
public class BaseTest {

    @Value("${base.url}")
    protected String baseUrl;

    // Acts as the "Browser," persisting cookies between requests
    protected final CookieFilter cookieFilter = new CookieFilter();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected String formKey;

    @BeforeEach
    public void setup() {
        RestAssured.baseURI = baseUrl;
        // Allows HTTPS in development environments without valid certificates
        RestAssured.useRelaxedHTTPSValidation();
    }

    /**
     * Accesses the cart page to start a PHP Session and extract the CSRF form_key.
     */
    protected void bootstrapSession() {
        log.info("--- [Step 0] Initializing Session (Bootstrap) ---");
        Response response = given()
                .filter(cookieFilter)
                .get("/default/checkout/cart/");

        String html = response.getBody().asString();

        // Regex to find the hidden input: <input name="form_key" type="hidden" value="..." />
        Matcher m = Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"").matcher(html);
        if (m.find()) {
            this.formKey = m.group(1);
            log.info("Session Active. FormKey: {}", formKey);
        } else {
            // Fallback: try to find it in the JS variable if the input doesn't exist
            Matcher m2 = Pattern.compile("\"formKey\":\"([^\"]+)\"").matcher(html);
            if(m2.find()) {
                this.formKey = m2.group(1);
                log.info("Session Active (via JS). FormKey: {}", formKey);
            } else {
                fail("Failed to find the Form Key. The application might be unavailable.");
            }
        }
    }

    /**
     * Parses the Cart Page HTML to find the window.checkoutConfig JSON
     * and extracts the Item ID for the specific SKU.
     */
    protected CartContext scrapeCartContext(String targetSku) {
        log.info("--- Extracting Cart data for SKU: {} ---", targetSku);

        Response response = given()
                .filter(cookieFilter)
                .get("/default/checkout/cart/");

        String html = response.getBody().asString();

        // FIX: Manual extraction of the JSON to avoid Regex errors on huge strings
        String jsonString = extractJsonConfig(html);

        if (jsonString == null) {
            fail("Could not find window.checkoutConfig on the cart page.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode quoteData = root.path("quoteData");
            JsonNode quoteItemData = root.path("quoteItemData");

            // 1. Get Quote IDs
            String quoteId = quoteData.path("entity_id").asText(); // Masked ID
            String realQuoteId = "";
            if (quoteItemData.isArray() && !quoteItemData.isEmpty()) {
                realQuoteId = quoteItemData.get(0).path("quote_id").asText();
            }

            // 2. Find the Item ID corresponding to the SKU
            String itemId = null;
            int qty = 0;
            double price = 0.0;

            if (quoteItemData.isArray()) {
                for (JsonNode item : quoteItemData) {
                    if (targetSku.equals(item.path("sku").asText())) {
                        itemId = item.path("item_id").asText();
                        qty = item.path("qty").asInt();
                        price = item.path("price").asDouble();
                        break;
                    }
                }
            }

            if (itemId == null) {
                fail("Item " + targetSku + " not found in the cart JSON.");
            }

            log.info("Extracted Data -> ItemID: {}, QuoteID: {}, Qty: {}", itemId, realQuoteId, qty);

            return CartContext.builder()
                    .formKey(this.formKey)
                    .quoteId(realQuoteId)
                    .maskedQuoteId(quoteId)
                    .itemId(itemId)
                    .qty(qty)
                    .price(price)
                    .build();

        } catch (Exception e) {
            // Debug log to help if it fails again
            String snippet = jsonString.length() > 500 ? jsonString.substring(0, 500) : jsonString;
            log.error("JSON Failure (Start): {}", snippet);
            fail("Failed to parse Cart JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the window.checkoutConfig JSON object by counting { } braces
     * to ensure the entire object is captured, even with special characters.
     */
    private String extractJsonConfig(String html) {
        String marker = "window.checkoutConfig =";
        int startIndex = html.indexOf(marker);

        if (startIndex == -1) return null;

        // Find where the JSON starts
        int openBraceIndex = html.indexOf("{", startIndex);
        if (openBraceIndex == -1) return null;

        int braceCount = 0;
        int closeBraceIndex = -1;

        // Iterate character by character to balance the braces
        for (int i = openBraceIndex; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    closeBraceIndex = i + 1; // Include the closing brace
                    break;
                }
            }
        }

        if (closeBraceIndex != -1) {
            return html.substring(openBraceIndex, closeBraceIndex);
        }

        return null;
    }
}