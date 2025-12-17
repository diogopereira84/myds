package com.fedex.automation.model.fedex;

import lombok.Builder;
import lombok.Data;

/**
 * DTO to store the data extracted from the cart page.
 * Uses Lombok to automatically generate Getters, Setters, and Builder.
 */
@Data
@Builder
public class CartContext {
    private String formKey;
    private String quoteId;       // Internal database ID (e.g., 457530)
    private String maskedQuoteId; // Masked entity ID (e.g., 09Zkqho...)
    private String itemId;        // Specific item ID (e.g., 329760) required for updates
    private int qty;
    private double price;
}