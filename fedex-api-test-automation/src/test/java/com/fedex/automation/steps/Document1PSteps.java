// src/test/java/com/fedex/automation/steps/Document1PSteps.java
package com.fedex.automation.steps;

import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.product.MenuHierarchyResponse;
import com.fedex.automation.model.fedex.product.StaticProductResponse;
import com.fedex.automation.service.fedex.*;
import com.fedex.automation.utils.TestResourceProvider;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;
import java.util.Map;

@Slf4j
public class Document1PSteps {

    @Autowired private CatalogService catalogService;
    @Autowired private ProductCatalogFlowService productCatalogFlowService;
    @Autowired private DocumentService documentService;
    @Autowired private RateService rateService;
    @Autowired private TemplateConfiguratorService templateConfiguratorService;
    @Autowired private TestContext testContext;
    @Autowired private TestResourceProvider testResourceProvider;

    @When("I search for the 1P product {string}")
    public void iSearchForThe1PProduct(String productName) {
        log.info("--- Search for 1P Product: {} ---", productName);
        String sku = catalogService.searchProductSku(productName, "1P");
        testContext.setCurrentSku(sku);
        testContext.setSellerModel("1P");
    }

    @And("I request an internal rate quote using the default template")
    public void iRequestAnInternalRateQuote() {
        rateService.rateProductEarly();
    }

    @And("I fetch the Menu Hierarchy to resolve the Product ID")
    public void iFetchTheMenuHierarchy() {
        log.info("--- Fetching Menu Hierarchy for Base ID: {} ---", testContext.getCurrentSku());
        MenuHierarchyResponse.ProductMenuDetail detail = productCatalogFlowService.resolveProductMenuDetailFromSku(testContext.getCurrentSku());

        testContext.setCurrentProductId(detail.getProductId());
        testContext.setCurrentProductVersion(detail.getVersion());
    }

    @And("I fetch the dynamic product details for the selected product")
    public void iFetchTheDynamicProductDetails() {
        log.info("--- Fetching dynamic product details ---");
        StaticProductResponse response = productCatalogFlowService.getProductDetails(
                testContext.getCurrentProductId(),
                testContext.getCurrentProductVersion()
        );
        testContext.setStaticProductDetails(response.getProduct());
    }

    @And("I initiate a 1P Configurator Session for the current product")
    public void iInitiateA1PConfiguratorSession() {
        templateConfiguratorService.createConfiguratorSession();
    }

    @And("I perform a Configurator Session Search")
    public void iPerformAConfiguratorSessionSearch() {
        templateConfiguratorService.searchConfiguratorSession();
    }

    @And("I upload the document {string} to the FedEx repository")
    public void iUploadTheDocumentToTheFedExRepository(String fileName) {
        log.info("--- Uploading Document: {} ---", fileName);
        File file = testResourceProvider.loadToTempFile("testdata/" + fileName);
        documentService.uploadDocument(file);
        documentService.convertToPrintReady();
    }

    @And("I create the Configurator State and apply the following features:")
    public void iCreateConfiguratorState(DataTable dataTable) throws Exception {
        Map<String, String> features = dataTable.asMap(String.class, String.class);
        templateConfiguratorService.createConfiguratorState(features);
    }

    @And("I add {int} configured document product\\(s) to the cart")
    public void iAddConfiguredDocumentProductSToTheCart(int quantity) {
        templateConfiguratorService.addConfiguredItemToCart(quantity);
    }

    @When("I configure and add the following 1P custom documents to the cart:")
    public void iConfigureAndAddTheFollowing1PCustomDocumentsToTheCart(DataTable dataTable) throws Exception {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            String documentName = row.get("documentName");
            int quantity = Integer.parseInt(row.get("quantity"));

            // Safely copy the map to filter out non-feature configuration columns
            Map<String, String> features = new java.util.HashMap<>(row);
            features.remove("productName");
            features.remove("documentName");
            features.remove("quantity");

            log.info("--- Orchestrating Add to Cart for: {} (Qty: {}) ---", productName, quantity);

            // Search
            String sku = catalogService.searchProductSku(productName, "1P");
            testContext.setCurrentSku(sku);
            testContext.setSellerModel("1P");

            // Resolve Product ID & Version Dynamically
            MenuHierarchyResponse.ProductMenuDetail detail = productCatalogFlowService.resolveProductMenuDetailFromSku(sku);
            testContext.setCurrentProductId(detail.getProductId());
            testContext.setCurrentProductVersion(detail.getVersion());

            // Fetch Domain Details (The Source of Truth) utilizing Version
            StaticProductResponse response = productCatalogFlowService.getProductDetails(detail.getProductId(), detail.getVersion());
            testContext.setStaticProductDetails(response.getProduct());

            // Create & Search Configurator Session
            templateConfiguratorService.createConfiguratorSession();
            templateConfiguratorService.searchConfiguratorSession();

            // Upload & Process Document
            File file = testResourceProvider.loadToTempFile("testdata/" + documentName);
            documentService.uploadDocument(file);
            documentService.convertToPrintReady();

            // Create State with the dynamic features mapped from the current BDD row
            templateConfiguratorService.createConfiguratorState(features);

            // Add strictly to the current session Cart
            templateConfiguratorService.addConfiguredItemToCart(quantity);

            log.info("--- Successfully added {} to cart ---", productName);
        }
    }
}