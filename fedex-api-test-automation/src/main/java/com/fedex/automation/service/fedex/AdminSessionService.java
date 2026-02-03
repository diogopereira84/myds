package com.fedex.automation.service.fedex;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
public class AdminSessionService {

    @Value("${base.url}")
    private String baseUrl;

    @Value("${admin.path}")
    private String adminPath;

    @Value("${admin.username}")
    private String username;

    @Value("${admin.password}")
    private String password;

    @Autowired
    private RequestSpecification defaultRequestSpec; // Inject

    private final CookieFilter adminCookieFilter = new CookieFilter();
    private String adminBearerToken;

    // Helper to create base admin request
    public RequestSpecification adminRequest() {
        return given()
                .spec(defaultRequestSpec) // <--- Applies cURL filter
                .baseUri(baseUrl)
                .filter(adminCookieFilter);
    }

    public void bootstrapAdminSession() {
        log.info("--- [Admin] Bootstrapping Admin Session ---");

        String loginUrl = baseUrl + adminPath + "/admin/auth/login/";
        Response pageResp = adminRequest().get(loginUrl);
        String formKey = extractFormKey(pageResp.asString());

        Response loginResp = adminRequest()
                .formParam("login[username]", username)
                .formParam("login[password]", password)
                .formParam("form_key", formKey)
                .post(loginUrl);

        if (loginResp.statusCode() != 200 && loginResp.statusCode() != 302) {
            fail("Admin Login failed! Status: " + loginResp.statusCode());
        }
        log.info("--- [Admin] Login Successful ---");
    }

    public String resolveEntityId(String incrementId) {
        if (adminBearerToken == null) {
            getAdminBearerToken();
        }

        log.info("Resolving Entity ID for Order #{}", incrementId);

        Response response = given()
                .spec(defaultRequestSpec) // <--- Applies cURL filter
                .baseUri(baseUrl)
                .header("Authorization", "Bearer " + adminBearerToken)
                .queryParam("searchCriteria[filter_groups][0][filters][0][field]", "increment_id")
                .queryParam("searchCriteria[filter_groups][0][filters][0][value]", incrementId)
                .get("/rest/V1/orders");

        int totalCount = response.jsonPath().getInt("total_count");
        if (totalCount == 0) {
            throw new RuntimeException("Order #" + incrementId + " not found via REST API.");
        }

        String entityId = response.jsonPath().getString("items[0].entity_id");
        log.info("Resolved Order #{} -> Entity ID: {}", incrementId, entityId);
        return entityId;
    }

    private void getAdminBearerToken() {
        Response response = given()
                .spec(defaultRequestSpec) // <--- Applies cURL filter
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .body("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}")
                .post("/rest/V1/integration/admin/token");

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get Admin Token: " + response.asString());
        }
        this.adminBearerToken = response.asString().replace("\"", "");
    }

    public String scrapeSendToMiraklUrl(String orderEntityId) {
        String url = baseUrl + adminPath + "/sales/order/view/order_id/" + orderEntityId;
        log.info("Accessing Order Page: {}", url);

        Response response = adminRequest().get(url);
        String html = response.asString();

        Pattern p = Pattern.compile("[\"']url[\"']\\s*:\\s*[\"']([^\"']*mirakl\\\\?/order\\\\?/send[^\"']*)[\"']");
        Matcher m = p.matcher(html);

        if (m.find()) {
            return m.group(1).replace("\\/", "/");
        }
        Pattern p2 = Pattern.compile("setLocation\\(['\"]([^'\"]*mirakl/order/send[^'\"]*)['\"]\\)");
        Matcher m2 = p2.matcher(html);
        if(m2.find()) {
            return m2.group(1);
        }
        throw new RuntimeException("Could not find 'Send to Mirakl' URL on page for ID " + orderEntityId);
    }

    private String extractFormKey(String html) {
        Pattern p = Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }
}