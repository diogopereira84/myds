package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.CartContext;
import com.fedex.automation.service.fedex.exception.CartErrorCode;
import com.fedex.automation.service.fedex.exception.CartOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartPayloadParser {

    private static final String CHECKOUT_CONFIG_MARKER = "window.checkoutConfig =";

    private final ObjectMapper objectMapper;

    public CartContext extractCartContext(String html, String targetSku, String formKey) {
        String jsonString = extractJsonConfig(html);
        if (jsonString == null) {
            throw new CartOperationException(CartErrorCode.PARSE_ERROR, "Could not find window.checkoutConfig on the cart page.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode quoteItemData = root.path("quoteItemData");

            String maskedQuoteId = root.path("quoteData").path("entity_id").asText("");
            String realQuoteId = quoteItemData.isArray() && !quoteItemData.isEmpty()
                    ? quoteItemData.get(0).path("quote_id").asText("")
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
                throw new CartOperationException(CartErrorCode.ITEM_NOT_FOUND, "Item SKU " + targetSku + " not found in the cart JSON.");
            }

            return CartContext.builder()
                    .formKey(formKey)
                    .quoteId(realQuoteId)
                    .maskedQuoteId(maskedQuoteId)
                    .itemId(itemId)
                    .qty(qty)
                    .build();
        } catch (CartOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new CartOperationException(CartErrorCode.PARSE_ERROR, "Failed to parse cart checkout config payload.", e);
        }
    }

    public CartSectionSummary parseCartSectionSummary(String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode cartNode = rootNode.path("cart");
            if (cartNode.isMissingNode() || cartNode.isNull()) {
                throw new CartOperationException(CartErrorCode.PARSE_ERROR, "Cart node missing in section load response.");
            }

            return new CartSectionSummary(
                    cartNode.path("summary_count").asInt(0),
                    cartNode.path("subtotal").asText("")
            );
        } catch (CartOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new CartOperationException(CartErrorCode.PARSE_ERROR, "Failed to parse cart section response.", e);
        }
    }

    private String extractJsonConfig(String html) {
        int startIndex = html.indexOf(CHECKOUT_CONFIG_MARKER);
        if (startIndex == -1) {
            return null;
        }

        int openBraceIndex = html.indexOf('{', startIndex);
        if (openBraceIndex == -1) {
            return null;
        }

        int braceCount = 0;
        for (int i = openBraceIndex; i < html.length(); i++) {
            char current = html.charAt(i);
            if (current == '{') {
                braceCount++;
            } else if (current == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return html.substring(openBraceIndex, i + 1);
                }
            }
        }

        return null;
    }

    public record CartSectionSummary(int summaryCount, String subtotal) {
    }
}
