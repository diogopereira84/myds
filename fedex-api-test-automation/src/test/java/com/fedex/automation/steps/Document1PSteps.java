package com.fedex.automation.steps;

import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.product.MenuHierarchyResponse;
import com.fedex.automation.model.fedex.product.StaticProductResponse;
import com.fedex.automation.service.fedex.*;
import com.fedex.automation.utils.StepAssertions;
import com.fedex.automation.utils.TestResourceProvider;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.fedex.automation.utils.ResponseValidator.assertOk;

@Slf4j
public class Document1PSteps {

    private static final String SELLER_MODEL_1P = "1P";
    private static final String KEY_PRODUCT_NAME = "productName";
    private static final String KEY_DOCUMENT_NAME = "documentName";
    private static final String KEY_QUANTITY = "quantity";
    private static final String TESTDATA_DIR = "testdata/";

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private ProductCatalogFlowService productCatalogFlowService;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private RateService rateService;
    @Autowired
    private TemplateConfiguratorService templateConfiguratorService;
    @Autowired
    private TestContext testContext;
    @Autowired
    private TestResourceProvider testResourceProvider;

    @When("I search for the 1P product {string}")
    public void iSearchForThe1PProduct(String productName) {
        log.info("--- Search for 1P Product: {} ---", productName);
        String sku = catalogService.searchProductSku(productName, SELLER_MODEL_1P);
        Assertions.assertNotNull(sku, "SKU not found for product: " + productName);
        testContext.setCurrentSku(sku);
        testContext.setSellerModel(SELLER_MODEL_1P);
    }

    @And("I request an internal rate quote using the default template")
    public void iRequestAnInternalRateQuote() {
        Response response = rateService.rateProductEarly();
        assertOk(response, "Internal rate request failed");
    }

    @And("I fetch the Menu Hierarchy to resolve the Product ID")
    public void iFetchTheMenuHierarchy() {
        String currentSku = testContext.getCurrentSku();
        Assertions.assertNotNull(currentSku, "Current SKU is missing. Ensure product search ran.");
        log.info("--- Fetching Menu Hierarchy for Base ID: {} ---", currentSku);
        MenuHierarchyResponse.ProductMenuDetail detail = productCatalogFlowService.resolveProductMenuDetailFromSku(currentSku);
        Assertions.assertNotNull(detail, "Menu hierarchy detail not found for SKU: " + currentSku);

        testContext.setCurrentProductId(detail.getProductId());
        testContext.setCurrentProductVersion(detail.getVersion());
    }

    @And("I fetch the dynamic product details for the selected product")
    public void iFetchTheDynamicProductDetails() {
        String productId = testContext.getCurrentProductId();
        String productVersion = testContext.getCurrentProductVersion();
        Assertions.assertNotNull(productId, "Current productId is missing. Ensure menu hierarchy resolution ran.");
        Assertions.assertNotNull(productVersion, "Current productVersion is missing. Ensure menu hierarchy resolution ran.");
        log.info("--- Fetching dynamic product details ---");
        StaticProductResponse response = productCatalogFlowService.getProductDetails(
                productId,
                productVersion
        );
        Assertions.assertNotNull(response, "Dynamic product details response is null.");
        Assertions.assertNotNull(response.getProduct(), "Dynamic product details missing product node.");
        testContext.setStaticProductDetails(response.getProduct());
    }

    @And("I initiate a 1P Configurator Session for the current product")
    public void iInitiateA1PConfiguratorSession() {
        startConfiguratorSession();
    }

    @And("I perform a Configurator Session Search")
    public void iPerformAConfiguratorSessionSearch() {
        searchConfiguratorSession();
    }

    @And("I upload the document {string} to the FedEx repository")
    public void iUploadTheDocumentToTheFedExRepository(String fileName) {
        log.info("--- Uploading Document: {} ---", fileName);
        uploadAndConvertDocument(fileName);
    }

    @And("I create the Configurator State and apply the following features:")
    public void iCreateConfiguratorState(DataTable dataTable) {
        Map<String, String> features = dataTable.asMap(String.class, String.class);
        createConfiguratorStateWithFeatures(features);
    }

    @And("I add {int} configured document product\\(s) to the cart")
    public void iAddConfiguredDocumentProductSToTheCart(int quantity) {
        addConfiguredItemToCart(quantity);
    }

    @When("I configure and add the following 1P custom documents to the cart:")
    public void iConfigureAndAddTheFollowing1PCustomDocumentsToTheCart(DataTable dataTable) {
        StepAssertions.assertTableNotEmpty(dataTable, "Custom documents table must contain at least one row.");
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            resetPerRowContext();
            StepAssertions.requireKeys(row, KEY_PRODUCT_NAME, KEY_DOCUMENT_NAME, KEY_QUANTITY);

            String productName = row.get(KEY_PRODUCT_NAME);
            String documentName = row.get(KEY_DOCUMENT_NAME);
            int quantity = StepAssertions.parsePositiveInt(row.get(KEY_QUANTITY), KEY_QUANTITY);

            Map<String, String> features = extractFeatures(row);

            log.info("--- Orchestrating Add to Cart for: {} (Qty: {}) ---", productName, quantity);

            resolveProductContext(productName);
            startConfiguratorSession();
            searchConfiguratorSession();
            uploadAndConvertDocument(documentName);
            createConfiguratorStateWithFeatures(features);
            addConfiguredItemToCart(quantity);

            log.info("--- Successfully added {} to cart ---", productName);
        }
    }

    private void resolveProductContext(String productName) {
        String sku = catalogService.searchProductSku(productName, SELLER_MODEL_1P);
        Assertions.assertNotNull(sku, "SKU not found for product: " + productName);
        testContext.setCurrentSku(sku);
        testContext.setSellerModel(SELLER_MODEL_1P);

        MenuHierarchyResponse.ProductMenuDetail detail = productCatalogFlowService.resolveProductMenuDetailFromSku(sku);
        Assertions.assertNotNull(detail, "Menu hierarchy detail not found for SKU: " + sku);
        testContext.setCurrentProductId(detail.getProductId());
        testContext.setCurrentProductVersion(detail.getVersion());

        StaticProductResponse response = productCatalogFlowService.getProductDetails(detail.getProductId(), detail.getVersion());
        Assertions.assertNotNull(response, "Dynamic product details response is null for SKU: " + sku);
        Assertions.assertNotNull(response.getProduct(), "Dynamic product details missing product node for SKU: " + sku);
        testContext.setStaticProductDetails(response.getProduct());
    }

    private void startConfiguratorSession() {
        Response sessionResponse = templateConfiguratorService.createConfiguratorSession();
        assertOk(sessionResponse, "Configurator session creation failed");
        Assertions.assertNotNull(testContext.getSessionId(), "Session ID must be set after configurator session creation.");
    }

    private void searchConfiguratorSession() {
        Response searchResponse = templateConfiguratorService.searchConfiguratorSession();
        assertOk(searchResponse, "Configurator session search failed");
    }

    private void uploadAndConvertDocument(String documentName) {
        File file = testResourceProvider.loadToTempFile(TESTDATA_DIR + documentName);
        documentService.uploadDocument(file);
        Assertions.assertNotNull(testContext.getOriginalDocId(), "Original document ID must be set after upload.");
        documentService.convertToPrintReady();
        Assertions.assertNotNull(testContext.getPrintReadyDocId(), "Print-ready document ID must be set after conversion.");
    }

    private void createConfiguratorStateWithFeatures(Map<String, String> features) {
        Response stateResponse = templateConfiguratorService.createConfiguratorState(features);
        assertOk(stateResponse, "Configurator state creation failed");
        Assertions.assertNotNull(testContext.getConfiguratorStateId(), "Configurator state ID must be set after state creation.");
    }

    private void addConfiguredItemToCart(int quantity) {
        Response cartResponse = templateConfiguratorService.addConfiguredItemToCart(quantity);
        assertOk(cartResponse, "Add configured item to cart failed");
    }

    private Map<String, String> extractFeatures(Map<String, String> row) {
        Map<String, String> features = new java.util.HashMap<>(row);
        features.remove(KEY_PRODUCT_NAME);
        features.remove(KEY_DOCUMENT_NAME);
        features.remove(KEY_QUANTITY);
        return features;
    }

    private void resetPerRowContext() {
        testContext.setSessionId(null);
        testContext.setConfiguratorStateId(null);
        testContext.setConfiguratorPayload(null);
        testContext.setOriginalDocId(null);
        testContext.setPrintReadyDocId(null);
        testContext.setCurrentSku(null);
        testContext.setCurrentProductId(null);
        testContext.setCurrentProductVersion(null);
        testContext.setStaticProductDetails(null);
        testContext.setSellerModel(null);
        testContext.setCurrentOfferId(null);
    }
}
