package com.fedex.automation.steps;

import com.fedex.automation.service.fedex.DocumentService;
import com.fedex.automation.service.fedex.TemplateConfiguratorService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Map;

@Slf4j
public class Document1PSteps {

    @Autowired private DocumentService documentService;
    @Autowired private TemplateConfiguratorService templateConfiguratorService;

    @When("I upload the document {string} to the FedEx repository")
    public void iUploadTheDocumentToTheFedExRepository(String fileName) {
        log.info("--- [Step] Upload Document: {} ---", fileName);
        File file = new File("src/test/resources/testdata/" + fileName);
        documentService.uploadDocument(file);
        documentService.convertToPrintReady();
    }

    @When("I configure the 1P document product {string} with the following features:")
    public void iConfigureThe1PDocumentProductWithTheFollowingFeatures(String productName, DataTable dataTable) throws Exception {
        log.info("--- [Step] Configure 1P Document Product: {} ---", productName);
        Map<String, String> features = dataTable.asMap(String.class, String.class);
        templateConfiguratorService.createConfiguratorState(productName, features);
    }

    @When("I add the configured document product to the cart")
    public void iAddTheConfiguredDocumentProductToTheCart() {
        log.info("--- [Step] Add Configured Document Product to Cart ---");
        templateConfiguratorService.addConfiguredItemToCart();
    }
}