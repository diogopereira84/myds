package com.fedex.automation.service.fedex;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguratorService {

    private final SessionService sessionService;
    private static final String CART_PRODUCT_ADD_ENDPOINT = "/default/cart/product/add";

    public void add1PConfiguredItemToCart(String sku, String partnerProductId, int quantity) {
        String configuratorStateId = UUID.randomUUID().toString();
        String configuratorSessionId = UUID.randomUUID().toString();
        String expirationTime = Instant.now().plusSeconds(86400).toString();

        log.info("Adding 1P Item [SKU: {}, PartnerID: {}, Qty: {}]", sku, partnerProductId, quantity);

        String jsonPayload = String.format(
                "{" +
                        "\"configuratorStateId\":\"%s\"," +
                        "\"expirationDateTime\":\"%s\"," +
                        "\"customDocumentDetails\":[]," +
                        "\"isEditable\":true," +
                        "\"product\":{" +
                        "\"id\":1568921842428," +
                        "\"version\":1," +
                        "\"name\":\"Flyers-Canva\"," +
                        "\"qty\":%d," +
                        "\"priceable\":true," +
                        "\"features\":[" +
                        "{\"id\":1448981549109,\"name\":\"Paper Size\",\"choice\":{\"id\":1463685362954,\"name\":\"5.5x8.5\",\"properties\":[{\"id\":1571841122054,\"name\":\"DISPLAY_HEIGHT\",\"value\":\"8.5\"},{\"id\":1571841164815,\"name\":\"DISPLAY_WIDTH\",\"value\":\"5.5\"},{\"id\":1449069906033,\"name\":\"MEDIA_HEIGHT\",\"value\":\"8.74\"},{\"id\":1449069908929,\"name\":\"MEDIA_WIDTH\",\"value\":\"5.74\"}]}}," +
                        "{\"id\":1448981549269,\"name\":\"Sides\",\"choice\":{\"id\":1448988124560,\"name\":\"Single-Sided\",\"properties\":[{\"id\":1461774376168,\"name\":\"SIDE\",\"value\":\"SINGLE\"},{\"id\":1471294217799,\"name\":\"SIDE_VALUE\",\"value\":\"1\"}]}}," +
                        "{\"id\":1448984679218,\"name\":\"Orientation\",\"choice\":{\"id\":1449000016192,\"name\":\"Vertical\",\"properties\":[{\"id\":1453260266287,\"name\":\"PAGE_ORIENTATION\",\"value\":\"PORTRAIT\"}]}}," +
                        "{\"id\":1448981549741,\"name\":\"Paper Type\",\"choice\":{\"id\":1448988664295,\"name\":\"Laser (32 lb.)\",\"properties\":[{\"id\":1450324098012,\"name\":\"MEDIA_TYPE\",\"value\":\"E32\"},{\"id\":1453234015081,\"name\":\"PAPER_COLOR\",\"value\":\"#FFFFFF\"}]}}," +
                        "{\"id\":1448981549581,\"name\":\"Print Color\",\"choice\":{\"id\":1448988600611,\"name\":\"Full Color\",\"properties\":[{\"id\":1453242778807,\"name\":\"PRINT_COLOR\",\"value\":\"COLOR\"}]}}" +
                        "]," +
                        "\"properties\":[" +
                        "{\"id\":1453895478444,\"name\":\"MIN_DPI\",\"value\":\"150.0\"}," +
                        "{\"id\":1464709502522,\"name\":\"PRODUCT_QTY_SET\",\"value\":\"%d\"}," +
                        "{\"id\":1750254073200,\"name\":\"TEMPLATE_VENDOR_CODE\"}," +
                        "{\"id\":1455050109631,\"name\":\"DEFAULT_IMAGE_HEIGHT\",\"value\":\"8.74\"}," +
                        "{\"id\":1614715469176,\"name\":\"IMPOSE_TEMPLATE_ID\",\"value\":\"12\"}," +
                        "{\"id\":1568041487844,\"name\":\"VENDOR_TEMPLATE\",\"value\":\"YES\"}," +
                        "{\"id\":1453243262198,\"name\":\"ENCODE_QUALITY\",\"value\":\"100\"}," +
                        "{\"id\":1455050109636,\"name\":\"DEFAULT_IMAGE_WIDTH\",\"value\":\"5.74\"}," +
                        "{\"id\":1453242488328,\"name\":\"ZOOM_PERCENTAGE\",\"value\":\"60\"}," +
                        "{\"id\":1453894861756,\"name\":\"LOCK_CONTENT_ORIENTATION\",\"value\":\"true\"}," +
                        "{\"id\":1470151626854,\"name\":\"SYSTEM_SI\",\"value\":\"ATTENTION:Use the following instructions...\"}" +
                        "]," +
                        "\"pageExceptions\":[]," +
                        "\"proofRequired\":false," +
                        "\"instanceId\":%d," +
                        "\"userProductName\":\"Flyers-Canva-Auto\"," +
                        "\"inserts\":[]," +
                        "\"exceptions\":[]," +
                        "\"addOns\":[]," +
                        "\"contentAssociations\":[{\"parentContentReference\":\"217c4306-0b65-11f1-8038-2da085f260c2\",\"contentReference\":\"22be444d-0b65-11f1-b14d-e291ef64be46\",\"contentType\":\"application/pdf\",\"fileSizeBytes\":0,\"fileName\":\"Untitled Design\",\"printReady\":true,\"contentReqId\":1455709847200,\"name\":\"Front_Side\",\"purpose\":\"SINGLE_SHEET_FRONT\",\"pageGroups\":[{\"start\":1,\"end\":1,\"width\":5.74,\"height\":8.74,\"orientation\":\"PORTRAIT\"}],\"physicalContent\":false}]," +
                        "\"productionContentAssociations\":[]," +
                        "\"products\":[]," +
                        "\"externalSkus\":[]," +
                        "\"isOutSourced\":false," +
                        "\"contextKeys\":[]," +
                        "\"designId\":\"DAHBgu3RDPo\"," +
                        "\"partnerProductId\":\"%s\"" +
                        "}," +
                        "\"integratorProductReference\":\"%s\"," +
                        "\"configuratorSessionId\":\"%s\"," +
                        "\"expressCheckoutButtonSelected\":false," +
                        "\"userWorkspace\":{\"files\":[],\"projects\":[]}," +
                        "\"errors\":[]," +
                        "\"changeProduct\":false," +
                        "\"loggedInUser\":false," +
                        "\"fxoProductInstance\":{\"quantityChoices\":[\"25\",\"50\",\"100\",\"250\",\"500\",\"1000\"]}" +
                        "}",
                configuratorStateId,
                expirationTime,
                quantity,
                quantity,
                System.currentTimeMillis(),
                partnerProductId,
                sku,
                configuratorSessionId
        );

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Adrum", "isAjax:true")
                .header("Origin", "https://staging2.office.fedex.com")
                .header("Referer", "https://staging2.office.fedex.com/default/configurator/index/index/responseid/" + configuratorStateId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .formParam("data", jsonPayload)
                .formParam("itemId", "")
                .post(CART_PRODUCT_ADD_ENDPOINT);

        if (response.statusCode() != 200) {
            log.error("Failed to add 1P item. Status: {}, Body: {}", response.statusCode(), response.body().asString());
        }
        assertEquals(200, response.statusCode(), "Expected 200 OK from Cart Product Add");
        log.info("Successfully added 1P item to cart.");
    }
}