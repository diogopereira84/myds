package com.fedex.automation.model.printful;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintfulCheckoutRequest {
    private String externalProductId;
    private String sessionId;
    private String categoryId;
    private List<PrintfulVariant> variantMap;
}