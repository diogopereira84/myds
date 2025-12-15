package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.CartContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;
    private final CatalogService catalogService;

    @Value("${endpoint.cart.add}")
    private String addEndpoint;

    @Value("${test.product.offer_id}")
    private String offerId;

    public void addToCart(String sku, String qty) {

        log.info("Adding SKU {} to cart...", sku);

        Map<String, String> params = new HashMap<>();
        params.put("form_key", sessionService.getFormKey());
        params.put("sku", sku);
        params.put("qty", qty);
        params.put("offer_id", offerId);
        params.put("punchout_disabled", "1");
        params.put("super_attribute", "");

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(params)
                .post(addEndpoint);

        assertEquals(302, response.statusCode(), "Add to cart should redirect");
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
            int qty = 0; // Initialize qty

            if (quoteItemData.isArray()) {
                for (JsonNode item : quoteItemData) {
                    if (targetSku.equals(item.path("sku").asText())) {
                        itemId = item.path("item_id").asText();
                        qty = item.path("qty").asInt(); // <--- FIX: Extract quantity here
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
                    .qty(qty) // <--- FIX: Set quantity in builder
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to scrape cart", e);
        }
    }

    /**
     * Extracts the JSON object by counting braces to ensure nested objects are handled correctly.
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
}