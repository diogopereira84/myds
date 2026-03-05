package com.fedex.automation.service.printful;

import com.fedex.automation.config.MiraklConfig;
import com.fedex.automation.config.PrintfulConfig;
import com.fedex.automation.model.printful.*;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintfulApparelService {

    private final RequestSpecification defaultRequestSpec;
    private final PrintfulConfig printfulConfig;

    @Value("${base.url}")
    private String fedexBaseUrl;

    public Response executePunchout(String sku, String offerId, String shopId) {
        String punchoutPath = String.format("/default/marketplacepunchout/index/index/sku/%s/offer_id/%s/seller_sku/%s/", sku, offerId, shopId);
        log.info("Executing Printful Punchout GET to: {}{}", fedexBaseUrl, punchoutPath);

        return given()
                .spec(defaultRequestSpec)
                .baseUri(fedexBaseUrl)
                .redirects().follow(false)
                .get(punchoutPath)
                .then()
                .statusCode(302)
                .extract()
                .response();
    }

    public Response followRedirect(String targetUrl, String phpSessId) {
        log.info("Manually following punchout redirect to: {}", targetUrl);

        var request = given().spec(defaultRequestSpec);

        if (phpSessId != null) {
            request.cookie("PHPSESSID", phpSessId);
        }

        return request.get(targetUrl)
                .then()
                .extract()
                .response();
    }


    public boolean validateSession(String sessionId, String phpSessId, String formKey) {
        log.info("--- Validating Custom Apparel Session for Session ID: {} (with Awaitility Polling) ---", sessionId);
        String requestBody = String.format("{\"sessionId\":\"%s\"}", sessionId);

        try {
            // Awaitility will execute the code inside until() until it returns true,
            // or until the timeout is reached.
            Awaitility.await()
                    .atMost(Duration.ofSeconds(printfulConfig.getRetryTimeoutSeconds()))
                    .pollInterval(Duration.ofSeconds(printfulConfig.getRetryIntervalSeconds()))
                    .until(() -> {
                        log.info("Polling Custom Apparel Session Validation API...");

                        Response response = given()
                                .spec(defaultRequestSpec)
                                .baseUri(printfulConfig.getCustomApparelBaseUrl())
                                .header("Accept", "application/json")
                                .header("Content-Type", "application/json")
                                .cookie("PHPSESSID", phpSessId)
                                .cookie("form_key", formKey)
                                .body(requestBody)
                                .post(printfulConfig.getValidateSessionEndpoint());

                        // If the third party returns a 5xx or 4xx, we don't crash, we just tell Awaitility to retry
                        if (response.statusCode() != 200) {
                            log.warn("Received HTTP {} from validation API. Retrying...", response.statusCode());
                            return false;
                        }

                        // Check if the body actually says "true"
                        boolean isValid = Boolean.parseBoolean(response.asString().trim());
                        if (!isValid) {
                            log.warn("Session returned false. It may still be generating. Retrying...");
                        }

                        return isValid;
                    });

            // If Awaitility finishes without throwing an exception, it successfully returned true!
            return true;

        } catch (ConditionTimeoutException e) {
            log.error("Session validation failed: Timed out after {} seconds of retrying.", printfulConfig.getRetryTimeoutSeconds());
            return false;
        }
    }

    public AuthNonceResponse generateNonce(String phpSessId, String formKey) {
        log.info("--- Generating Auth Nonce ---");

        Response response = given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getCustomApparelBaseUrl())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .cookie("PHPSESSID", phpSessId)
                .cookie("form_key", formKey)
                .body("{}")
                .post(printfulConfig.getGenerateNonceEndpoint())
                .then()
                .statusCode(200)
                .extract()
                .response();

        return response.as(AuthNonceResponse.class);
    }
    public Response uploadDesignAsset(String sessionId, String phpSessId, String formKey) {
        log.info("--- Simulating Asset Upload for Session: {} ---", sessionId);

        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getCustomApparelBaseUrl())
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie("PHPSESSID", phpSessId)
                .cookie("form_key", formKey)
                .formParam("uploadType", "embedded-designer")
                .formParam("fileName", "random.jpg")
                .queryParam("session_id", sessionId)
                .post("/rpc/embedded-designer-rpc/file-library-upload")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    public Response submitApparelCheckout(PrintfulCheckoutRequest requestPayload, String phpSessId, String formKey) {
        log.info("--- Submitting Printful Custom Apparel Checkout ---");

        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getCustomApparelBaseUrl())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .cookie("PHPSESSID", phpSessId)
                .cookie("form_key", formKey)
                .body(requestPayload)
                .post(printfulConfig.getOrderCheckoutEndpoint())
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    public S3UploadCredentialsResponse getS3UploadCredentials(String nonce) {
        log.info("--- Step 0: Requesting Printful S3 Upload Credentials ---");
        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getPrintfulBaseUrl())
                .header("Authorization", "Bearer " + nonce)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8") // Added Content-Type
                .formParam("uploadType", "embedded-designer") // Added payload body
                .formParam("fileName", "random.jpg")          // Added payload body
                .post("/rpc/embedded-designer-rpc/file-library-upload")
                .then()
                .statusCode(200)
                .extract()
                .as(S3UploadCredentialsResponse.class);
    }

    public Response uploadFileToS3(S3UploadCredentialsResponse.S3Credentials creds, java.io.File file) {
        log.info("--- Step 1: Uploading File to S3 Bucket ---");
        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getPrintfulS3Url())
                .multiPart("success_action_status", creds.getSuccessActionStatus())
                .multiPart("acl", creds.getAcl())
                .multiPart("key", creds.getKey())
                .multiPart("X-Amz-Credential", creds.getXAmzCredential())
                .multiPart("X-Amz-Algorithm", creds.getXAmzAlgorithm())
                .multiPart("X-Amz-Date", creds.getXAmzDate())
                .multiPart("Policy", creds.getPolicy())
                // Only attach signature if Printful provided one
                .multiPart("X-Amz-Signature", creds.getXAmzSignature() != null ? creds.getXAmzSignature() : "")
                .multiPart("file", file)
                .post()
                .then()
                .statusCode(201) // AWS S3 returns 201 Created on success
                .extract()
                .response();
    }

    public PrintfulFileCallbackResponse fileLibraryUploadCallback(
            String nonce,
            String temporaryFileId,
            String fileType,
            long fileSize,
            String fileName,
            String location,
            String bucket,
            String key,
            String etag) {

        log.info("--- Step 2: Printful File Upload Callback ---");

        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getPrintfulBaseUrl())
                .header("Authorization", "Bearer " + nonce)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("temporaryFileId", temporaryFileId)
                .formParam("file[type]", fileType)
                .formParam("file[size]", String.valueOf(fileSize))
                .formParam("file[filename]", fileName)
                .formParam("location", location)
                .formParam("bucket", bucket)
                .formParam("key", key)
                .formParam("etag", etag)
                .post("/rpc/embedded-designer-rpc/file-library-upload-callback")
                .then()
                .statusCode(200)
                .extract()
                .as(PrintfulFileCallbackResponse.class);
    }

    public void fileLibraryGetUploadedFile(String nonce, String temporaryFileKey) {
        log.info("--- Step 3: Verifying Uploaded File in Printful Library (with Awaitility Polling) ---");
        String requestBody = String.format("{\"temporaryFileKey\":\"%s\"}", temporaryFileKey);

        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(printfulConfig.getRetryTimeoutSeconds()))
                    .pollInterval(Duration.ofSeconds(printfulConfig.getRetryIntervalSeconds()))
                    .until(() -> {
                        log.info("Polling Printful File Library for temporaryFileKey: {}...", temporaryFileKey);

                        Response response = given()
                                .spec(defaultRequestSpec)
                                .baseUri(printfulConfig.getPrintfulBaseUrl())
                                .header("Authorization", "Bearer " + nonce)
                                .contentType("application/json")
                                .body(requestBody)
                                .post("/rpc/embedded-designer-rpc/file-library-get-uploaded-file");

                        if (response.statusCode() != 200) {
                            log.warn("Received HTTP {} from file verification API. Retrying...", response.statusCode());
                            return false;
                        }

                        // Parse the JSON. If Printful is still processing, it returns {"result": {"success": 0}}
                        Integer successFlag = response.jsonPath().get("result.success");
                        if (successFlag != null && successFlag == 0) {
                            log.warn("Printful is still processing the file (success: 0). Retrying...");
                            return false;
                        }

                        log.info("File successfully verified by Printful!");
                        return true;
                    });

        } catch (ConditionTimeoutException e) {
            log.error("File verification failed: Timed out after {} seconds of retrying.", printfulConfig.getRetryTimeoutSeconds());
        }
    }

    public PrintfulCatalogResponse getCatalogProducts(String categoryId) {
        log.info("--- Fetching Printful Catalog Products for Category ID: {} ---", categoryId);

        return given()
                .spec(defaultRequestSpec) // <--- THIS triggers your framework's CurlLoggingFilter
                .baseUri("https://api.printful.com")
                .header("Authorization", "Bearer " + printfulConfig.getPrintfulApiToken())
                .header("X-PF-Store-ID", printfulConfig.getPrintfulStoreId())
                .queryParam("category_ids", categoryId)
                .get("/v2/catalog-products")
                .then()
                .statusCode(200)
                .extract()
                .as(PrintfulCatalogResponse.class);
    }

    public PrintfulCatalogVariantsResponse getCatalogVariants(String productId) {
        log.info("--- Fetching Printful Catalog Variants for Product ID: {} ---", productId);

        return given()
                .spec(defaultRequestSpec)
                .baseUri("https://api.printful.com") // Targets api.printful.com
                .header("Authorization", "Bearer " + printfulConfig.getPrintfulApiToken())
                .header("X-PF-Store-ID", printfulConfig.getPrintfulStoreId())
                .queryParam("limit", 100)
                .get("/v2/catalog-products/" + productId + "/catalog-variants")
                .then()
                .statusCode(200)
                .extract()
                .as(PrintfulCatalogVariantsResponse.class);
    }

    public PrintfulProductPricesResponse getProductPrices(String productId, String phpSessId, String formKey) {
        log.info("--- Fetching Printful Product Prices for Product ID: {} ---", productId);

        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getCustomApparelBaseUrl())
                .cookie("PHPSESSID", phpSessId)
                .cookie("form_key", formKey)
                .queryParam("productId", productId)
                .get("/api/catalog/product-prices/")
                .then()
                .statusCode(200)
                .extract()
                .as(PrintfulProductPricesResponse.class);
    }

    public Response submitCheckout(PrintfulCheckoutRequest checkoutPayload, String phpSessId, String formKey) {
        log.info("--- Submitting Printful Custom Apparel Checkout ---");

        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getCustomApparelBaseUrl())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .cookie("PHPSESSID", phpSessId)
                .cookie("form_key", formKey)
                .body(checkoutPayload)
                .post("/api/order/checkout")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }
}