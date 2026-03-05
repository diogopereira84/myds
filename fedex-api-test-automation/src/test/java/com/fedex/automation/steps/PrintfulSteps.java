package com.fedex.automation.steps;

import com.fedex.automation.config.MiraklConfig;
import com.fedex.automation.constants.PrintfulConstants;
import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.printful.AuthNonceResponse;
import com.fedex.automation.model.printful.PrintfulCatalogVariantsResponse;
import com.fedex.automation.model.printful.PrintfulCheckoutRequest;
import com.fedex.automation.model.printful.PrintfulVariant;
import com.fedex.automation.service.mirakl.OfferService;
import com.fedex.automation.service.printful.PrintfulApparelService;
import com.fedex.automation.utils.PrintfulCheckoutHelper;
import com.fedex.automation.utils.PrintfulExtractorUtil;
import com.fedex.automation.utils.PrintfulPayloadMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PrintfulSteps {

    private final OfferService offerService;
    private final PrintfulApparelService printfulApparelService;
    private final TestContext testContext;
    private final MiraklConfig miraklConfig;

    @And("^I resolve the Mirakl offer details for the following Printful products:$")
    public void iResolveTheMiraklOfferDetails(DataTable dataTable) {
        List<Map<String, String>> products = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> product : products) {
            String productName = product.get("category");
            log.info("--- Resolving Mirakl Offer for Product: {} ---", productName);

            String sku = testContext.getSkuByProductName(productName);
            if (sku == null) {
                throw new IllegalStateException("Failed to find SKU for product name: " + productName);
            }

            try {
                var offer = offerService.getOfferFromShop(miraklConfig.getPrintfulShopId(), sku);
                testContext.setCurrentSku(sku);
                testContext.setCurrentOfferId(String.valueOf(offer.getOfferId()));
                testContext.setShopSku(offer.getShopSku());
                testContext.setShopId(offer.getShopId());

                log.info("Resolved Offer ID: {} and Shop SKU: {} and Shop Id: {} for Printful SKU: {}",
                        testContext.getCurrentOfferId(), testContext.getShopSku(), testContext.getShopId(), sku);
            } catch (Exception e) {
                log.error("Failed to fetch Mirakl offer data for SKU {}.", sku, e);
                throw new IllegalStateException("Failed to dynamically fetch Mirakl offer data. Halting test.", e);
            }
        }
    }

    @And("^I upload a design asset to the Printful asset library$")
    public void iUploadADesignAssetToPrintfulS3Library() {
        var authNonceResponse = testContext.getAuthNonceResponse();

        if (authNonceResponse == null || authNonceResponse.getNonce() == null) {
            throw new IllegalStateException("Auth Nonce is missing! Generate Auth Nonce must be run first.");
        }
        String nonce = authNonceResponse.getNonce();

        // Target the local test file to upload
        java.io.File uploadFile = new java.io.File("src/test/resources/testdata/random.jpg");
        if (!uploadFile.exists()) {
            throw new IllegalStateException("Test file does not exist at path: " + uploadFile.getAbsolutePath());
        }

        // --- Step 0: Get Credentials ---
        var credsResponse = printfulApparelService.getS3UploadCredentials(nonce);
        var creds = credsResponse.getResult();

        if (creds == null || creds.getTemporaryFileId() == null) {
            throw new IllegalStateException("Failed to retrieve S3 Upload Credentials from Printful.");
        }

        // --- Step 1: Upload directly to Amazon S3 ---
        Response s3Response = printfulApparelService.uploadFileToS3(creds, uploadFile);

        // Parse the AWS S3 XML Response
        io.restassured.path.xml.XmlPath xmlPath = s3Response.xmlPath();
        String s3Location = xmlPath.getString("PostResponse.Location");
        String s3Bucket = xmlPath.getString("PostResponse.Bucket");
        String s3Key = xmlPath.getString("PostResponse.Key");
        String rawEtag = xmlPath.getString("PostResponse.ETag");

        // AWS returns ETag with quotes (e.g., "hash"). We must strip them for Printful.
        String cleanEtag = rawEtag != null ? rawEtag.replace("\"", "") : "";

        // File metadata
        String fileName = uploadFile.getName();
        long fileSize = uploadFile.length();
        String mimeType = fileName.endsWith(".pdf") ? "application/pdf" : "image/jpeg";

        // --- Step 2: Callback to Printful ---
        var callbackResponse = printfulApparelService.fileLibraryUploadCallback(
                nonce,
                creds.getTemporaryFileId(),
                mimeType,
                fileSize,
                fileName,
                s3Location,
                s3Bucket,
                s3Key,
                cleanEtag
        );

        String temporaryFileKey = callbackResponse.getResult().getTemporaryFileKey();

        if (temporaryFileKey == null) {
            throw new IllegalStateException("Failed to retrieve temporaryFileKey from Printful Callback. Printful returned success: 0.");
        }

        // --- Step 3: Verify File Availability ---
        printfulApparelService.fileLibraryGetUploadedFile(nonce, temporaryFileKey);

        // Save the key for the final checkout payload
        testContext.setPrintfulTemporaryFileKey(temporaryFileKey);

        log.info("Successfully Printful S3 Upload! Temporary File Key: {}", temporaryFileKey);
    }
    @And("^I validate the Custom Apparel session$")
    public void iValidateTheCustomApparelSession() {
        log.info("--- Executing Session Validation ---");

        boolean isValid = printfulApparelService.validateSession(testContext.getPrintfulSessionId(),
                testContext.getPrintfulPhpSessIdCookie(),
                testContext.getPrintfulFormKeyCookie()
        );

        if (!isValid) {
            throw new IllegalStateException("Custom Apparel session validation failed (API returned false).");
        }
        log.info("Custom Apparel session successfully validated.");
    }

    @And("^I generate an auth nonce for the Custom Apparel session$")
    public void iGenerateAnAuthNonce() {
        log.info("--- Executing Auth Nonce Generation ---");

        AuthNonceResponse nonceResponse = printfulApparelService.generateNonce(
                testContext.getPrintfulPhpSessIdCookie(),
                testContext.getPrintfulFormKeyCookie()
        );
        testContext.setExternalProductId(nonceResponse.getExternalProductId());
        if (nonceResponse.getNonce() == null) {
            throw new IllegalStateException("Failed to generate Auth Nonce. Response was null or missing nonce token.");
        }



        // Save the result model into context for later stages!
        testContext.setAuthNonceResponse(nonceResponse);
        log.info("Successfully Generated Auth Nonce: {} for the externalProductId: {}", nonceResponse.getNonce(), nonceResponse.getExternalProductId());
    }

    @When("^I execute the Printful punchout for the resolved products$")
    public void iExecuteThePrintfulPunchout() {
        String sku = testContext.getCurrentSku();
        String offerId = testContext.getCurrentOfferId();
        String shopId = testContext.getShopId();

        if (sku == null || offerId == null || shopId == null) {
            throw new IllegalStateException("Missing required IDs. Ensure 'I resolve the Mirakl offer details' was run first.");
        }

        // 1. Execute Punchout
        Response redirectResponse = printfulApparelService.executePunchout(sku, offerId, shopId);

        // 2. Harvest PHPSESSID
        if (redirectResponse.getCookies().containsKey(PrintfulConstants.COOKIE_PHPSESSID)) {
            testContext.setPrintfulPhpSessIdCookie(redirectResponse.getCookies().get(PrintfulConstants.COOKIE_PHPSESSID));
        }

        // 3. Follow Redirect
        String redirectLocation = redirectResponse.getHeader("Location");
        if (redirectLocation == null) {
            throw new IllegalStateException("Expected a 302 redirect with a Location header, but none was found.");
        }

        Response finalResponse = printfulApparelService.followRedirect(redirectLocation, testContext.getPrintfulPhpSessIdCookie());
        testContext.setLastResponse(finalResponse);

        // 4. Delegate Extractions to Utility
        extractAndSetContextData(finalResponse, redirectLocation);
    }

    @And("^I initialize the Printful design session with a random picture$")
    public void iInitializeThePrintfulDesignSession() {
        String sessionId = testContext.getPrintfulSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("Printful sessionId is null! Punchout must be executed first.");
        }

        Response designResponse = printfulApparelService.uploadDesignAsset(
                sessionId,
                testContext.getPrintfulPhpSessIdCookie(),
                testContext.getPrintfulFormKeyCookie()
        );

        testContext.setLastResponse(designResponse);
        log.info("Successfully attached design asset to session: {}", sessionId);
    }

    @When("^I configure the Printful apparel variants and proceed to checkout:$")
    public void iConfigureThePrintfulApparelVariants(DataTable dataTable) {
        PrintfulCheckoutRequest checkoutPayload = PrintfulCheckoutRequest.builder()
                .externalProductId(testContext.getExternalProductId())
                .sessionId(testContext.getPrintfulSessionId())
                .categoryId(PrintfulConstants.PRINTFUL_CATEGORY_ID)
                .variantMap(PrintfulPayloadMapper.mapDataTableToVariants(dataTable))
                .build();

        Response response = printfulApparelService.submitApparelCheckout(
                checkoutPayload,
                testContext.getPrintfulPhpSessIdCookie(),
                testContext.getPrintfulFormKeyCookie()
        );

        log.info("Apparel checkout response status: {}", response.statusCode());
        testContext.setLastResponse(response);
    }

    // --- Private Orchestration Helper ---

    private void extractAndSetContextData(Response response, String redirectLocation) {
        String responseBody = response.asString();

        // 1. Strictly Extract Form Key
        String formKey = PrintfulExtractorUtil.extractFormKey(response, redirectLocation);
        if (formKey == null) {
            throw new IllegalStateException("Extraction Failure: Could not extract form_key from cookies, Location header, or HTML body.");
        }
        testContext.setPrintfulFormKeyCookie(formKey);
        log.info("Extracted form_key: {}", formKey);

        // 2. Strictly Extract Session ID
        String sessionId = PrintfulExtractorUtil.extractSessionId(redirectLocation, responseBody);
        if (sessionId == null) {
            throw new IllegalStateException("Extraction Failure: Could not dynamically extract Printful session_id from response.");
        }
        testContext.setPrintfulSessionId(sessionId);
        log.info("Extracted Printful Session ID: {}", sessionId);

        // 3. Strictly Extract External Product ID
        String extProductId = PrintfulExtractorUtil.extractExternalProductId(redirectLocation, responseBody);
        if (extProductId == null) {
            throw new IllegalStateException("Extraction Failure: Could not dynamically extract External Product ID (UUID) from response.");
        }
       // testContext.setExternalProductId(extProductId);
      //  log.info("Extracted External Product ID: {}", extProductId);
    }
    @And("^I configure the Printful apparel variant:$")
    public void iConfigureThePrintfulApparelVariant(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        Map<String, String> row = rows.get(0);

        String expectedProductName = row.get("productName");
        String expectedColor = row.get("color");
        String expectedTechnique = row.get("techniques"); // Extract the new column

        // Strictly retrieve the Shop SKU / Category ID from Context
        String shopSkuCategoryId = testContext.getShopSku();
        testContext.setPrintfulSelectedColor(expectedColor);
        testContext.setPrintfulSelectedTechnique(expectedTechnique); // Save to context

        if (shopSkuCategoryId == null || shopSkuCategoryId.trim().isEmpty()) {
            throw new IllegalStateException("Shop SKU not found in context! Ensure 'I resolve the Mirakl offer details' was run before this step so the Seller Model is populated.");
        }

        log.info("--- Configuring Printful Variant: {} - {} using Category ID: {} ---", expectedProductName, expectedColor, shopSkuCategoryId);

        // 1. Fetch the Catalog (This will log the cURL via defaultRequestSpec)
        var catalogResponse = printfulApparelService.getCatalogProducts(shopSkuCategoryId);

        if (catalogResponse == null || catalogResponse.getData() == null || catalogResponse.getData().isEmpty()) {
            throw new IllegalStateException("Printful Catalog API returned an empty or null response for Category ID: " + shopSkuCategoryId);
        }

        // 2. Filter the JSON Data for the matching product
        var matchedProduct = catalogResponse.getData().stream()
                .filter(p -> !p.isDiscontinued()) // Must not be discontinued
                .filter(p -> p.getName() != null && p.getName().startsWith(expectedProductName)) // Name must start with the BDD input
                .filter(p -> p.getColors() != null && p.getColors().stream().anyMatch(c -> c.getName().equalsIgnoreCase(expectedColor))) // Must contain the target color
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No valid active Printful catalog product found matching name '" + expectedProductName + "' and color '" + expectedColor + "'"));

        // 3. Save the resolved ID (e.g. 146) to context
        testContext.setPrintfulProductId(String.valueOf(matchedProduct.getId()));
        testContext.setPrintfulSelectedColor(expectedColor);

        log.info("Successfully resolved Printful Product ID: {} for Color: {} and Technique: {}",
                testContext.getPrintfulProductId(), expectedColor, expectedTechnique);
     }

    @And("^I select quantities and proceed to checkout:$")
    public void iSelectQuantitiesAndProceedToCheckout(DataTable dataTable) {
        log.info("--- Selecting Quantities (Preparing for Checkout) ---");

        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        Map<String, String> quantityMap = rows.get(0);

        java.util.Map<String, Integer> selectedQuantities = new java.util.HashMap<>();

        // Loop through the data table columns (Sizes: S, M, L, etc.)
        for (Map.Entry<String, String> entry : quantityMap.entrySet()) {
            String size = entry.getKey();
            int qty = Integer.parseInt(entry.getValue());

            // because the checkout payload requires the full variant matrix.
            selectedQuantities.put(size, qty);
            log.info("Mapped Size: {} | Requested Quantity: {}", size, qty);
        }

        if (selectedQuantities.isEmpty()) {
            throw new IllegalArgumentException("The quantities data table cannot be empty!");
        }

        // Save the full map of 8 sizes to the context
        testContext.setPrintfulSelectedQuantities(selectedQuantities);
        log.info("All {} sizes mapped and saved. Ready for ID resolution.", selectedQuantities.size());
    }

    @When("^I add to cart or checkout the Printful apparel variants$")
    public void iAddToCartOrCheckoutThePrintfulApparelVariants() {
        log.info("--- Mapping Variant IDs and Fetching Dynamic Pricing for Checkout ---");

        String productId = testContext.getPrintfulProductId();
        String targetColor = testContext.getPrintfulSelectedColor();
        String targetTechnique = testContext.getPrintfulSelectedTechnique();
        Map<String, Integer> selectedQuantities = testContext.getPrintfulSelectedQuantities();

        // 1. Fetch the variants matching the color and sizes (returns a list of 8 variants with just IDs/Sizes)
        // (This uses the code from the previous iteration to build 'baseCheckoutVariants')
        var variantsResponse = printfulApparelService.getCatalogVariants(productId);
        List<PrintfulCatalogVariantsResponse.CatalogVariant> colorVariants = variantsResponse.getData().stream()
                .filter(v -> targetColor.equalsIgnoreCase(v.getColor()))
                .toList();

        List<PrintfulVariant> baseCheckoutVariants = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : selectedQuantities.entrySet()) {
            var matchedVariant = colorVariants.stream()
                    .filter(v -> entry.getKey().equalsIgnoreCase(v.getSize()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing variant ID for size: " + entry.getKey()));

            baseCheckoutVariants.add(PrintfulVariant.builder()
                    .variantId(matchedVariant.getId())
                    .size(entry.getKey())
                    .amount(entry.getValue())
                    .build());
        }

        // 2. Fetch the Pricing API
        var pricingResponse = printfulApparelService.getProductPrices(
                productId,
                testContext.getPrintfulPhpSessIdCookie(),
                testContext.getPrintfulFormKeyCookie()
        );

        // 3. Delegate the complex price extraction and difference calculation to the Helper
        List<PrintfulVariant> fullyPricedVariants = PrintfulCheckoutHelper.buildVariantMapWithPricing(
                baseCheckoutVariants,
                pricingResponse,
                targetTechnique
        );

        // 4. Build the final JSON Payload
        PrintfulCheckoutRequest checkoutPayload = PrintfulCheckoutRequest.builder()
                .externalProductId(testContext.getExternalProductId()) // fb269248-3bd2...
                .sessionId(testContext.getPrintfulSessionId())         // 6cab8bbf3788...
                .categoryId(testContext.getShopSku())              // "18"
                .variantMap(fullyPricedVariants)
                .build();

        // 5. Submit the Checkout!
        Response checkoutResponse = printfulApparelService.submitCheckout(
                checkoutPayload,
                testContext.getPrintfulPhpSessIdCookie(),
                testContext.getPrintfulFormKeyCookie()
        );

        log.info("Apparel Checkout Response Status: {}", checkoutResponse.statusCode());
        testContext.setLastResponse(checkoutResponse);
    }
}