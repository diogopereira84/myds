// src/main/java/com/fedex/automation/constants/FedExConstants.java
package com.fedex.automation.constants;

public final class FedExConstants {
    private FedExConstants() {} // Prevent instantiation

    // Client IDs
    public static final String API_GATEWAY_CLIENT_ID = "l735d628c13a154cc2abab4ecc50fe0558";
    public static final String MAGENTO_UI_CLIENT_ID = "l7e4acbdd6b7d341b0b59234bbdbd4e82e";
    public static final String INTEGRATOR_ID_POD2 = "POD2.0";

    // Headers
    public static final String HEADER_CLIENT_ID = "client_id";
    public static final String PARAM_CLIENT_NAME = "ClientName";

    public static final String ENDPOINT_CONFIG_SESSIONS = "/application/fedexoffice/v2/configuratorsessions";
    public static final String ENDPOINT_CONFIG_SEARCH = "/application/fedexoffice/v2/configuratorsessionsearch";
    public static final String ENDPOINT_CONFIG_STATES = "/application/fedexoffice/v2/configuratorstates";

}