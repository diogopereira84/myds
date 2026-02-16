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

        // 2. Execute Request
        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.JSON)
                .header("accept", "*/*")
                .header("accept-language", "en-US,en;q=0.9")
                .header("adrum", "isAjax:true")
                .header("x-requested-with", "XMLHttpRequest")
                .body("{\"addressInformation\":{\"address\":{}}}")
                .post(endpoint);

        // 3. Log Response
        log.info("Cart Totals Response Code: {}", response.statusCode());
        if (log.isDebugEnabled()) {
            log.debug("Cart Totals Response Body: {}", response.body().asPrettyString());
        }

        assertEquals(200, response.statusCode(), "Expected 200 OK from Cart Totals Information");
    }

    /**
     * Adds an item to the cart using the form POST endpoint.
     */
    public void addToCart(String sku, String qty, String offerId) {
        log.info("Adding to Cart: SKU={}, Qty={}, OfferID={}", sku, qty, offerId);

        // 2. Prepare Form Data
        Map<String, String> params = new HashMap<>();
        params.put("form_key", sessionService.getFormKey());
        params.put("sku", sku);
        params.put("qty", qty);
        params.put("offer_id", offerId); // Use dynamic ID
        params.put("punchout_disabled", "1");
        params.put("super_attribute", "");

        // 2. Send Request
        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(params)
                .post(addEndpoint);

        // 3. Validate
        if (response.statusCode() != 302) {
            log.error("Add to Cart failed. Status: {}, Body: {}", response.statusCode(), response.body().asString());
        }
        assertEquals(302, response.statusCode(), "Add to cart should redirect (302)");
        log.info("Item successfully added to cart (302 Redirect received).");
    }

    /**
     * Scrapes the Cart page to build the CartContext (Quote IDs, Item ID).
     */
    public CartContext scrapeCartContext(String targetSku) {
        log.info("Scraping Cart Context, looking for SKU: {}", targetSku);

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
                fail("Item SKU " + targetSku + " not found in the cart JSON.");
            }

            log.info("Cart Context Captured: QuoteID={}, MaskedID={}, ItemID={}, Qty={}",
                    realQuoteId, maskedQuoteId, itemId, qty);

            return CartContext.builder()
                    .formKey(sessionService.getFormKey())
                    .quoteId(realQuoteId)
                    .maskedQuoteId(maskedQuoteId)
                    .itemId(itemId)
                    .qty(qty)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse cart JSON config", e);
            throw new RuntimeException("Failed to scrape cart", e);
        }
    }

    /**
     * Helper to get the Masked Cart ID.
     * Uses cache if available, otherwise fetches cart page.
     */
    private String getOrFetchMaskedCartId() {
        if (cachedMaskedCartId != null) {
            return cachedMaskedCartId;
        }

        log.info("Masked Cart ID not found in context. Fetching from Cart Page...");
        Response response = sessionService.authenticatedRequest()
                .get("/default/checkout/cart/");

        String html = response.body().asString();
        String extractedId = extractMaskedIdRegex(html);

        if (extractedId != null) {
            this.cachedMaskedCartId = extractedId;
            log.info("Extracted Masked Cart ID: {}", cachedMaskedCartId);
            return cachedMaskedCartId;
        } else {
            throw new RuntimeException("Failed to extract Masked Cart ID from /checkout/cart/ page. Verify the session is active.");
        }
    }

    private String extractMaskedIdRegex(String html) {
        Pattern pattern = Pattern.compile("\"maskedQuoteId\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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