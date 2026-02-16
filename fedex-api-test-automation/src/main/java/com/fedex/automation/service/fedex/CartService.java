package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.CartContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;
    private String cachedMaskedCartId; // Stores the ID to avoid re-scraping

    @Value("${endpoint.cart.add}")
    private String addEndpoint;

    /**
     * Automates the 'totals-information' CURL.
     * Uses the Masked Cart ID (e.g., otOrjqodDVcQ88dhpDjbfYVyMTqk5G86).
     */
    public void checkCartTotalsInformation() {
        // 1. Ensure we have the Masked Cart ID
        String maskedCartId = getOrFetchMaskedCartId();
        log.info("Checking Cart Totals for Masked ID: {}", maskedCartId);

        String endpoint = "/default/rest/default/V1/guest-carts/" + maskedCartId + "/totals-information";

        // 2. Execute Request (Matches your CURL)
        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.JSON)
                .header("accept", "*/*")
                .header("accept-language", "en-US,en;q=0.9")
                .header("adrum", "isAjax:true")
                .header("x-requested-with", "XMLHttpRequest") // Standard for this call
                .body("{\"addressInformation\":{\"address\":{}}}")
                .post(endpoint);

        // 3. Log Response as requested
        log.info("Cart Totals Response Code: {}", response.statusCode());
        log.info("Cart Totals Response Body: {}", response.body().asPrettyString());

        assertEquals(200, response.statusCode(), "Expected 200 OK from Cart Totals Information");
    }

    /**
     * Helper to get the Masked Cart ID.
     * If we haven't scraped it yet, we quickly fetch the cart page to find it.
     */
    private String getOrFetchMaskedCartId() {
        if (cachedMaskedCartId != null) {
            return cachedMaskedCartId;
        }

        log.info("Masked Cart ID not found in context. Fetching from Cart Page...");
        Response response = sessionService.authenticatedRequest()
                .get("/default/checkout/cart/");

        String html = response.body().asString();

        // Regex to find "maskedQuoteId": "..." in the checkoutConfig JSON on the page
        // Standard Magento 2 pattern
        Pattern pattern = Pattern.compile("\"maskedQuoteId\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);

        if (matcher.find()) {
            cachedMaskedCartId = matcher.group(1);
            log.info("Extracted Masked Cart ID: {}", cachedMaskedCartId);
            return cachedMaskedCartId;
        } else {
            throw new RuntimeException("Failed to extract Masked Cart ID from /checkout/cart/ page. verify the session is active.");
        }
    }



    public void addToCart(String sku, String qty, String offerId) {

        log.info("Adding SKU {} to cart...", sku);



        // 2. Prepare Form Data
        Map<String, String> params = new HashMap<>();
        params.put("form_key", sessionService.getFormKey());
        params.put("sku", sku);
        params.put("qty", qty);
        params.put("offer_id", offerId); // Use dynamic ID
        params.put("punchout_disabled", "1");
        params.put("super_attribute", "");

        // 3. Send Request
        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(params)
                .post(addEndpoint);

        assertEquals(302, response.statusCode(), "Add to cart should redirect (302)");
    }

    public CartContext scrapeCartContext(String targetSku) {

        Response response = sessionService.authenticatedRequest().get("/default/checkout/cart/");
        String html = response.asString();

        String jsonString = extractJsonConfig(html);

        if (jsonString == null) {
            fail("Could not find window.checkoutConfig on the cart page.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode quoteItemData = root.path("quoteItemData");

            String maskedQuoteId = root.path("quoteData").path("entity_id").asText();
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

            if (itemId == null) {
                fail("Item " + targetSku + " not found in the cart JSON.");
            }

            return CartContext.builder()
                    .formKey(sessionService.getFormKey())
                    .quoteId(realQuoteId)
                    .maskedQuoteId(maskedQuoteId)
                    .itemId(itemId)
                    .qty(qty)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to scrape cart", e);
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
}