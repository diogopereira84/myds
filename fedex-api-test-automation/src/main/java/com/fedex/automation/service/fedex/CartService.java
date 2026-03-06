package com.fedex.automation.service.fedex;

import com.fedex.automation.model.fedex.AddToCartRequest;
import com.fedex.automation.model.fedex.CartContext;
import com.fedex.automation.service.fedex.client.CartApiClient;
import com.fedex.automation.service.fedex.exception.CartErrorCode;
import com.fedex.automation.service.fedex.exception.CartOperationException;
import com.fedex.automation.service.fedex.parser.CartPayloadParser;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final SessionService sessionService;
    private final CartApiClient cartApiClient;
    private final CartPayloadParser cartPayloadParser;
    private final Clock clock;

    public void addToCart(String sku, String qty, String offerId) {
        int parsedQty;
        try {
            parsedQty = Integer.parseInt(qty);
        } catch (NumberFormatException ex) {
            throw new CartOperationException(CartErrorCode.INVALID_QUANTITY, "Quantity must be a valid integer but was: " + qty, ex);
        }

        AddToCartRequest request;
        try {
            request = AddToCartRequest.builder()
                    .formKey(sessionService.getFormKey())
                    .sku(sku)
                    .quantity(parsedQty)
                    .offerId(offerId)
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new CartOperationException(CartErrorCode.INVALID_REQUEST, "Invalid add-to-cart request.", ex);
        }

        addToCart(request);
    }

    public void addToCart(AddToCartRequest request) {
        if (request == null) {
            throw new CartOperationException(CartErrorCode.INVALID_REQUEST, "Add-to-cart request cannot be null.");
        }

        log.info("Adding to Cart: SKU={}, Qty={}, OfferID={}", request.getSku(), request.getQuantity(), request.getOfferId());
        cartApiClient.addToCartExpectRedirect(request);
        log.info("Item successfully added to cart (302 Redirect received).");
    }

    public CartContext scrapeCartContext(String targetSku) {
        log.info("Scraping Cart Context, looking for SKU: {}", targetSku);

        String cartPageHtml = cartApiClient.requestCartPageBody();
        CartContext context = cartPayloadParser.extractCartContext(cartPageHtml, targetSku, sessionService.getFormKey());

        log.info("Cart Context Captured: QuoteID={}, MaskedID={}, ItemID={}, Qty={}",
                context.getQuoteId(), context.getMaskedQuoteId(), context.getItemId(), context.getQty());

        return context;
    }

    public void verifyItemInCart() {
        log.info("--- [Validation] Verifying Magento Cart via Section Load ---");

        String responseBody = cartApiClient.requestCustomerSectionForValidationBody(clock.millis());
        CartPayloadParser.CartSectionSummary summary = cartPayloadParser.parseCartSectionSummary(responseBody);

        if (summary.summaryCount() <= 0) {
            throw new CartOperationException(
                    CartErrorCode.EMPTY_CART,
                    "Cart is completely empty! The 'Add to Cart' operation failed silently on the backend."
            );
        }

        log.info("SUCCESS! Verified {} item(s) in the Magento cart! Total: {}",
                summary.summaryCount(), summary.subtotal());
    }

    public Response loadCustomerSection() {
        log.info("--- [Action] Loading Customer Section UI Data ---");
        return cartApiClient.requestCustomerSectionUi(String.valueOf(clock.millis()));
    }
}
