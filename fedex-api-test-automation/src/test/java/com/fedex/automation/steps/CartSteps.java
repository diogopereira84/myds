package com.fedex.automation.steps;

import com.fedex.automation.service.fedex.CartService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@RequiredArgsConstructor
public class CartSteps {

    private final CartService cartService;

    @And("I verify the customer load section contains following information:")
    public void iVerifyTheCustomerLoadSectionContainsFollowingInformation(DataTable dataTable) {
        Response response = cartService.loadCustomerSection();
        response.then().statusCode(200);
        JsonPath jsonPath = response.jsonPath();
        Map<String, String> expectedData = dataTable.asMap(String.class, String.class);

        expectedData.forEach((path, expectedValue) -> {
            String actualValue = jsonPath.getString(path);

            assertNotNull(actualValue, "The JSON path '" + path + "' returned null or was not found in the response.");
            assertEquals(expectedValue, actualValue,
                    String.format("Mismatch at path [%s]! Expected: %s, Actual: %s", path, expectedValue, actualValue));

            log.info("Verified JSON Path [{}] matches expected value: {}", path, expectedValue);
        });
    }
}