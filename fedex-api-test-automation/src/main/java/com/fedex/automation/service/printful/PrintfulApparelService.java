package com.fedex.automation.service.printful;

import com.fedex.automation.config.FedexConfig;
import com.fedex.automation.config.PrintfulConfig;
import com.fedex.automation.model.printful.AuthNonceResponse;
import com.fedex.automation.model.printful.PrintfulCatalogResponse;
import com.fedex.automation.model.printful.PrintfulCatalogVariantsResponse;
import com.fedex.automation.model.printful.PrintfulCheckoutRequest;
import com.fedex.automation.model.printful.PrintfulProductPricesResponse;
import com.fedex.automation.model.printful.S3UploadCredentialsResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintfulApparelService {

    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_X_REQUESTED_WITH = "X-Requested-With";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded; charset=UTF-8";
    private static final String COOKIE_PHP_SESSION_ID = "PHPSESSID";
    private static final String COOKIE_FORM_KEY = "form_key";
    private static final String PATH_FILE_LIBRARY_UPLOAD = "/rpc/embedded-designer-rpc/file-library-upload";
    private static final String PATH_FILE_LIBRARY_UPLOAD_CALLBACK = "/rpc/embedded-designer-rpc/file-library-upload-callback";
    private static final String PATH_FILE_LIBRARY_GET_UPLOADED_FILE = "/rpc/embedded-designer-rpc/file-library-get-uploaded-file";
    private static final String PATH_CATALOG_PRODUCTS = "/v2/catalog-products";
    private static final String PATH_PRODUCT_PRICES = "/api/catalog/product-prices/";

    private final RequestSpecification defaultRequestSpec;
    private final PrintfulConfig printfulConfig;
    private final FedexConfig fedexConfig;

    public Response executePunchout(String sku, String offerId, String shopSku) {
        requireNonBlank(sku, "sku");
        requireNonBlank(offerId, "offerId");
        requireNonBlank(shopSku, "shopSku");

        String punchoutPath = String.format(
                "/default/marketplacepunchout/index/index/sku/%s/offer_id/%s/seller_sku/%s/",
                sku,
                offerId,
                shopSku
        );
        log.info("Executing Printful Punchout GET to: {}{}", fedexConfig.getBaseUrl(), punchoutPath);

        return given()
                .spec(defaultRequestSpec)
                .baseUri(fedexConfig.getBaseUrl())
                .redirects().follow(false)
                .get(punchoutPath)
                .then()
                .statusCode(302)
                .extract()
                .response();
    }

    public Response followRedirect(String targetUrl, String phpSessId) {
        requireNonBlank(targetUrl, "targetUrl");
        log.info("Manually following punchout redirect to: {}", targetUrl);

        RequestSpecification request = given().spec(defaultRequestSpec);
        if (hasText(phpSessId)) {
            request.cookie(COOKIE_PHP_SESSION_ID, phpSessId);
        }

        return request.get(targetUrl)
                .then()
                .extract()
                .response();
    }

    public boolean validateSession(String sessionId, String phpSessId, String formKey) {
        requireNonBlank(sessionId, "sessionId");
        requireNonBlank(phpSessId, "phpSessId");
        requireNonBlank(formKey, "formKey");

        log.info("--- Validating Custom Apparel Session for Session ID: {} (with Awaitility Polling) ---", sessionId);
        String requestBody = String.format("{\"sessionId\":\"%s\"}", sessionId);

        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(printfulConfig.getRetryTimeoutSeconds()))
                    .pollInterval(Duration.ofSeconds(printfulConfig.getRetryIntervalSeconds()))
                    .until(() -> {
                        log.info("Polling Custom Apparel Session Validation API...");

                        Response response = customApparelJsonRequest(phpSessId, formKey)
                                .body(requestBody)
                                .post(printfulConfig.getValidateSessionEndpoint());

                        if (response.statusCode() != 200) {
                            log.warn("Received HTTP {} from validation API. Retrying...", response.statusCode());
                            return false;
                        }

                        boolean isValid = Boolean.parseBoolean(response.asString().trim());
                        if (!isValid) {
                            log.warn("Session returned false. It may still be generating. Retrying...");
                        }
                        return isValid;
                    });
            return true;
        } catch (ConditionTimeoutException e) {
            String message = "Session validation failed: timed out after " + printfulConfig.getRetryTimeoutSeconds() + " seconds.";
            log.error(message);
            throw new IllegalStateException(message, e);
        }
    }

    public AuthNonceResponse generateNonce(String phpSessId, String formKey) {
        requireNonBlank(phpSessId, "phpSessId");
        requireNonBlank(formKey, "formKey");
        log.info("--- Generating Auth Nonce ---");

        Response response = customApparelJsonRequest(phpSessId, formKey)
                .body("{}")
                .post(printfulConfig.getGenerateNonceEndpoint())
                .then()
                .statusCode(200)
                .extract()
                .response();

        return response.as(AuthNonceResponse.class);
    }

    public Response uploadDesignAsset(String sessionId, String phpSessId, String formKey) {
        requireNonBlank(sessionId, "sessionId");
        requireNonBlank(phpSessId, "phpSessId");
        requireNonBlank(formKey, "formKey");
        log.info("--- Simulating Asset Upload for Session: {} ---", sessionId);

        return customApparelCookieRequest(phpSessId, formKey)
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_X_REQUESTED_WITH, "XMLHttpRequest")
                .formParam("uploadType", "embedded-designer")
                .formParam("fileName", "random.jpg")
                .queryParam("session_id", sessionId)
                .post(PATH_FILE_LIBRARY_UPLOAD)
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    public Response submitApparelCheckout(PrintfulCheckoutRequest requestPayload, String phpSessId, String formKey) {
        return submitCheckout(requestPayload, phpSessId, formKey);
    }

    public S3UploadCredentialsResponse getS3UploadCredentials(String nonce, String fileName) {
        requireNonBlank(nonce, "nonce");
        requireNonBlank(fileName, "fileName");
        log.info("--- Requesting Printful S3 Upload Credentials ---");

        return printfulWebBearerFormRequest(nonce)
                .formParam("uploadType", "embedded-designer")
                .formParam("fileName", fileName)
                .post(PATH_FILE_LIBRARY_UPLOAD)
                .then()
                .statusCode(200)
                .extract()
                .as(S3UploadCredentialsResponse.class);
    }

    public Response uploadFileToS3(S3UploadCredentialsResponse.S3Credentials creds, File file) {
        if (creds == null) {
            throw new IllegalArgumentException("creds must not be null");
        }
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("file must exist before S3 upload");
        }

        log.info("--- Uploading File to S3 Bucket ---");
        RequestSpecification request = given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getPrintfulS3Url())
                .multiPart("success_action_status", creds.getSuccessActionStatus())
                .multiPart("acl", creds.getAcl())
                .multiPart("key", creds.getKey())
                .multiPart("X-Amz-Credential", creds.getXAmzCredential())
                .multiPart("X-Amz-Algorithm", creds.getXAmzAlgorithm())
                .multiPart("X-Amz-Date", creds.getXAmzDate())
                .multiPart("Policy", creds.getPolicy());

        String signature = creds.getXAmzSignature();
        if (hasText(signature)) {
            request.multiPart("X-Amz-Signature", signature);
        }

        return request
                .multiPart("file", file)
                .post()
                .then()
                .statusCode(201)
                .extract()
                .response();
    }

    public Response fileLibraryUploadCallback(String nonce, FileUploadCallbackRequest callbackRequest) {
        requireNonBlank(nonce, "nonce");
        if (callbackRequest == null) {
            throw new IllegalArgumentException("callbackRequest must not be null");
        }
        validateCallbackRequest(callbackRequest);

        log.info("--- Printful File Upload Callback ---");

        return printfulWebBearerFormRequest(nonce)
                .formParam("temporaryFileId", callbackRequest.temporaryFileId())
                .formParam("file[type]", callbackRequest.fileType())
                .formParam("file[size]", String.valueOf(callbackRequest.fileSize()))
                .formParam("file[filename]", callbackRequest.fileName())
                .formParam("location", callbackRequest.location())
                .formParam("bucket", callbackRequest.bucket())
                .formParam("key", callbackRequest.key())
                .formParam("etag", callbackRequest.etag())
                .post(PATH_FILE_LIBRARY_UPLOAD_CALLBACK)
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    public Response fileLibraryUploadCallback(
            String nonce,
            String temporaryFileId,
            String fileType,
            long fileSize,
            String fileName,
            String location,
            String bucket,
            String key,
            String etag) {

        FileUploadCallbackRequest callbackRequest = new FileUploadCallbackRequest(
                temporaryFileId,
                fileType,
                fileSize,
                fileName,
                location,
                bucket,
                key,
                etag
        );

        return fileLibraryUploadCallback(nonce, callbackRequest);
    }

    public void fileLibraryGetUploadedFile(String nonce, String temporaryFileKey) {
        requireNonBlank(nonce, "nonce");
        requireNonBlank(temporaryFileKey, "temporaryFileKey");

        log.info("--- Verifying Uploaded File in Printful Library (with Awaitility Polling) ---");
        String requestBody = String.format("{\"temporaryFileKey\":\"%s\"}", temporaryFileKey);

        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(printfulConfig.getRetryTimeoutSeconds()))
                    .pollInterval(Duration.ofSeconds(printfulConfig.getRetryIntervalSeconds()))
                    .until(() -> {
                        log.info("Polling Printful File Library for temporaryFileKey: {}...", temporaryFileKey);

                        Response response = printfulWebBearerJsonRequest(nonce)
                                .body(requestBody)
                                .post(PATH_FILE_LIBRARY_GET_UPLOADED_FILE);

                        if (response.statusCode() != 200) {
                            log.warn("Received HTTP {} from file verification API. Retrying...", response.statusCode());
                            return false;
                        }

                        Integer successFlag = response.jsonPath().get("result.success");
                        if (successFlag != null && successFlag == 0) {
                            log.warn("Printful is still processing the file (success: 0). Retrying...");
                            return false;
                        }

                        log.info("File successfully verified by Printful!");
                        return true;
                    });

        } catch (ConditionTimeoutException e) {
            String message = "File verification failed: Timed out after " + printfulConfig.getRetryTimeoutSeconds() + " seconds of retrying.";
            log.error(message);
            throw new IllegalStateException(message, e);
        }
    }

    public PrintfulCatalogResponse getCatalogProducts(String categoryId) {
        requireNonBlank(categoryId, "categoryId");
        log.info("--- Fetching Printful Catalog Products for Category ID: {} ---", categoryId);

        return printfulApiRequest()
                .queryParam("category_ids", categoryId)
                .get(PATH_CATALOG_PRODUCTS)
                .then()
                .statusCode(200)
                .extract()
                .as(PrintfulCatalogResponse.class);
    }

    public PrintfulCatalogVariantsResponse getCatalogVariants(String productId) {
        requireNonBlank(productId, "productId");
        log.info("--- Fetching Printful Catalog Variants for Product ID: {} ---", productId);

        return printfulApiRequest()
                .queryParam("limit", 100)
                .get(PATH_CATALOG_PRODUCTS + "/" + productId + "/catalog-variants")
                .then()
                .statusCode(200)
                .extract()
                .as(PrintfulCatalogVariantsResponse.class);
    }

    public PrintfulProductPricesResponse getProductPrices(String productId, String phpSessId, String formKey) {
        requireNonBlank(productId, "productId");
        requireNonBlank(phpSessId, "phpSessId");
        requireNonBlank(formKey, "formKey");

        log.info("--- Fetching Printful Product Prices for Product ID: {} ---", productId);

        return customApparelCookieRequest(phpSessId, formKey)
                .queryParam("productId", productId)
                .get(PATH_PRODUCT_PRICES)
                .then()
                .statusCode(200)
                .extract()
                .as(PrintfulProductPricesResponse.class);
    }

    public Response submitCheckout(PrintfulCheckoutRequest checkoutPayload, String phpSessId, String formKey) {
        if (checkoutPayload == null) {
            throw new IllegalArgumentException("checkoutPayload must not be null");
        }
        requireNonBlank(phpSessId, "phpSessId");
        requireNonBlank(formKey, "formKey");

        log.info("--- Submitting Printful Custom Apparel Checkout ---");

        return customApparelJsonRequest(phpSessId, formKey)
                .body(checkoutPayload)
                .post(printfulConfig.getOrderCheckoutEndpoint())
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    private RequestSpecification customApparelCookieRequest(String phpSessId, String formKey) {
        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getCustomApparelBaseUrl())
                .cookie(COOKIE_PHP_SESSION_ID, phpSessId)
                .cookie(COOKIE_FORM_KEY, formKey);
    }

    private RequestSpecification customApparelJsonRequest(String phpSessId, String formKey) {
        return customApparelCookieRequest(phpSessId, formKey)
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
    }

    private RequestSpecification printfulWebBearerFormRequest(String nonce) {
        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getPrintfulWebBaseUrl())
                .header(HEADER_AUTHORIZATION, "Bearer " + nonce)
                .contentType(CONTENT_TYPE_FORM);
    }

    private RequestSpecification printfulWebBearerJsonRequest(String nonce) {
        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getPrintfulWebBaseUrl())
                .header(HEADER_AUTHORIZATION, "Bearer " + nonce)
                .contentType(CONTENT_TYPE_JSON);
    }

    private RequestSpecification printfulApiRequest() {
        return given()
                .spec(defaultRequestSpec)
                .baseUri(printfulConfig.getPrintfulApiBaseUrl())
                .header(HEADER_AUTHORIZATION, "Bearer " + printfulConfig.getPrintfulApiToken())
                .header("X-PF-Store-ID", printfulConfig.getPrintfulStoreId());
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static final class FileUploadCallbackRequest {
        private final String temporaryFileId;
        private final String fileType;
        private final long fileSize;
        private final String fileName;
        private final String location;
        private final String bucket;
        private final String key;
        private final String etag;

        public FileUploadCallbackRequest(
                String temporaryFileId,
                String fileType,
                long fileSize,
                String fileName,
                String location,
                String bucket,
                String key,
                String etag) {
            this.temporaryFileId = temporaryFileId;
            this.fileType = fileType;
            this.fileSize = fileSize;
            this.fileName = fileName;
            this.location = location;
            this.bucket = bucket;
            this.key = key;
            this.etag = etag;
        }

        public String temporaryFileId() {
            return temporaryFileId;
        }

        public String fileType() {
            return fileType;
        }

        public long fileSize() {
            return fileSize;
        }

        public String fileName() {
            return fileName;
        }

        public String location() {
            return location;
        }

        public String bucket() {
            return bucket;
        }

        public String key() {
            return key;
        }

        public String etag() {
            return etag;
        }
    }

    private static void validateCallbackRequest(FileUploadCallbackRequest callbackRequest) {
        requireNonBlank(callbackRequest.temporaryFileId(), "temporaryFileId");
        requireNonBlank(callbackRequest.fileType(), "fileType");
        requireNonBlank(callbackRequest.fileName(), "fileName");
        requireNonBlank(callbackRequest.location(), "location");
        requireNonBlank(callbackRequest.bucket(), "bucket");
        requireNonBlank(callbackRequest.key(), "key");
        requireNonBlank(callbackRequest.etag(), "etag");
        if (callbackRequest.fileSize() <= 0) {
            throw new IllegalArgumentException("fileSize must be greater than 0");
        }
    }
}
