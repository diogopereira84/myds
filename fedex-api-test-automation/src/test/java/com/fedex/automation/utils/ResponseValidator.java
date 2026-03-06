package com.fedex.automation.utils;

import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;

public final class ResponseValidator {

    private ResponseValidator() {
    }

    public static void assertOk(Response response, String message) {
        Assertions.assertNotNull(response, "Response is null.");
        Assertions.assertEquals(200, response.statusCode(), message + ": " + response.asString());
    }
}

