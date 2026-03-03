package com.fedex.automation.steps;

import com.fedex.automation.service.fedex.CartService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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

            // --- NEW: Custom Interceptor for Total Quantity Sum ---
            if ("cart.total_quantity".equalsIgnoreCase(path)) {
                List<Number> quantities = jsonPath.getList("cart.items.qty", Number.class);
                int actualTotal = (quantities != null) ? quantities.stream().mapToInt(Number::intValue).sum() : 0;

                assertEquals(Integer.parseInt(expectedValue), actualTotal,
                        String.format("Mismatch for total item quantity! Expected: %s, Actual: %d", expectedValue, actualTotal));

                log.info("Verified Custom Path [{}] matches expected sum: {}", path, expectedValue);
                return; // Acts as a 'continue' in a forEach loop
            }

            // --- Original Behavior for exact JsonPath matching ---
            String actualValue = jsonPath.getString(path);

            assertNotNull(actualValue, "The JSON path '" + path + "' returned null or was not found in the response.");
            assertEquals(expectedValue, actualValue,
                    String.format("Mismatch at path [%s]! Expected: %s, Actual: %s", path, expectedValue, actualValue));

            log.info("Verified JSON Path [{}] matches expected value: {}", path, expectedValue);
        });
    }
}