// src/test/java/com/fedex/automation/steps/Document1PSteps.java
package com.fedex.automation.steps;

import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.product.StaticProductResponse;
import com.fedex.automation.service.fedex.*;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Map;

@Slf4j
public class Document1PSteps {

    @Autowired private CatalogService catalogService;
    @Autowired private ProductCatalogFlowService productCatalogFlowService;
    @Autowired private DocumentService documentService;
    @Autowired private RateService rateService;
    @Autowired private TemplateConfiguratorService templateConfiguratorService;
    @Autowired private TestContext testContext;

    @When("I search for the 1P product {string}")
    public void iSearchForThe1PProduct(String productName) {
        log.info("--- [Call 1] Search for 1P Product: {} ---", productName);
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
        log.info("--- [Call 3] Fetching Menu Hierarchy for Base ID: {} ---", testContext.getCurrentSku());
        String productId = productCatalogFlowService.resolveProductIdFromSku(testContext.getCurrentSku());
        testContext.setCurrentProductId(productId);
    }

    @And("I fetch the dynamic product details for the selected product")
    public void iFetchTheDynamicProductDetails() {
        log.info("--- [Call 4] Fetching dynamic product details ---");
        StaticProductResponse response = productCatalogFlowService.getProductDetails(testContext.getCurrentProductId());
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
        log.info("--- [Call 7 & 8] Uploading Document: {} ---", fileName);
        File file = new File("src/test/resources/testdata/" + fileName);
        documentService.uploadDocument(file);
        documentService.convertToPrintReady();
    }

    @And("I create the Configurator State and apply the following features:")
    public void iCreateConfiguratorState(DataTable dataTable) throws Exception {
        Map<String, String> features = dataTable.asMap(String.class, String.class);
        templateConfiguratorService.createConfiguratorState("MultiSheet", features);
    }

    @And("I add {int} configured document product\\(s) to the cart")
    public void iAddConfiguredDocumentProductSToTheCart(int quantity) {
        templateConfiguratorService.addConfiguredItemToCart(quantity);
    }
}