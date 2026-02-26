package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.constants.FedExConstants;
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

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final SessionService sessionService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectMapper objectMapper;
    private String cachedMaskedCartId;

    @Value("${endpoint.cart.get}")
    private String cartGetEndpoint;

    @Value("${endpoint.cart.add.3p}")
    private String cartAdd3PEndpoint;

    @Value("${endpoint.customer.section.load}")
    private String customerSectionLoadEndpoint;


    public void checkCart() {
        log.info("Checking Cart Totals");

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.JSON)
                .header("accept", "*/*")
                .get(cartGetEndpoint);

        log.info("Cart Totals Response Code: {}", response.statusCode());
        assertEquals(200, response.statusCode(), "Expected 200 OK from Cart Totals Information");
    }

    public void addToCart(String sku, String qty, String offerId) {
        log.info("Adding to Cart: SKU={}, Qty={}, OfferID={}", sku, qty, offerId);

        Map<String, String> params = new HashMap<>();
        params.put("form_key", sessionService.getFormKey());
        params.put("sku", sku);
        params.put("qty", qty);
        params.put("offer_id", offerId);
        params.put("punchout_disabled", "1");
        params.put("super_attribute", "");

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .formParams(params)
                .post(cartAdd3PEndpoint);

        if (response.statusCode() != 302) {
            log.error("Add to Cart failed. Status: {}, Body: {}", response.statusCode(), response.body().asString());
        }
        assertEquals(302, response.statusCode(), "Add to cart should redirect (302)");
        log.info("Item successfully added to cart (302 Redirect received).");
    }

    public CartContext scrapeCartContext(String targetSku) {
        log.info("Scraping Cart Context, looking for SKU: {}", targetSku);

        Response response = sessionService.authenticatedRequest().get(cartGetEndpoint);
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

            log.info("Cart Context Captured: QuoteID={}, MaskedID={}, ItemID={}, Qty={}", realQuoteId, maskedQuoteId, itemId, qty);

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

    private String getOrFetchMaskedCartId() {
        if (cachedMaskedCartId != null) return cachedMaskedCartId;

        Response response = sessionService.authenticatedRequest().get(cartGetEndpoint);
        String extractedId = extractMaskedIdRegex(response.body().asString());

        if (extractedId != null) {
            this.cachedMaskedCartId = extractedId;
            return cachedMaskedCartId;
        } else {
            throw new RuntimeException("Failed to extract Masked Cart ID");
        }
    }

    private String extractMaskedIdRegex(String html) {
        Pattern pattern = Pattern.compile("\"maskedQuoteId\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
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
                if (braceCount == 0) return html.substring(openBraceIndex, i + 1);
            }
        }
        return null;
    }

    public void verifyItemInCart() {
        log.info("--- [Validation] Verifying Magento Cart via Section Load ---");
        long timestamp = System.currentTimeMillis();

        Response response = sessionService.authenticatedRequest()
                .baseUri(sessionService.getBaseUrl())
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .header("Referer", sessionService.getBaseUrl() + cartGetEndpoint)
                .queryParam("sections", "cart,messages")
                .queryParam("force_new_section_timestamp", "true")
                .queryParam("_", String.valueOf(timestamp))
                .get(customerSectionLoadEndpoint);

        assertEquals(200, response.statusCode(), "Customer section load failed with HTTP " + response.statusCode());

        try {
            JsonNode rootNode = mapper.readTree(response.asString());
            JsonNode cartNode = rootNode.path("cart");
            assertFalse(cartNode.isMissingNode(), "Cart node missing in section load response");

            int summaryCount = cartNode.path("summary_count").asInt(0);

            if (summaryCount == 0) {
                fail("Cart is completely empty! The 'Add to Cart' operation failed silently on the backend.");
            } else {
                log.info("SUCCESS! Verified {} item(s) in the Magento cart! Total: {}",
                        summaryCount, cartNode.path("subtotal").asText());
            }
        } catch (Exception e) {
            fail("Failed to parse cart section response", e);
        }
    }

    public Response loadCustomerSection() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        log.info("--- [Action] Loading Customer Section UI Data ---");

        return sessionService.checkoutRequest()
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .queryParam("sections", "messages,company,cart,session,marketplace,sso_section")
                .queryParam("force_new_section_timestamp", "true")
                .queryParam("_", timestamp)
                .get(customerSectionLoadEndpoint);
    }
}