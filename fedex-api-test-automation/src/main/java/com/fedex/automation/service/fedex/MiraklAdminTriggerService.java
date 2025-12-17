package com.fedex.automation.service.fedex;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MiraklAdminTriggerService {

    private final AdminSessionService adminSessionService;

    public void triggerSendToMirakl(String orderIncrementId) {
        // 1. Login
        adminSessionService.bootstrapAdminSession();

        // 2. Resolve ID (Fixes the 404/Redirect issue)
        String entityId = adminSessionService.resolveEntityId(orderIncrementId);

        // 3. Scrape & Trigger
        log.info("--- [Admin] Scraping Secure 'Send' URL for Entity ID {} ---", entityId);
        String secureUrl = adminSessionService.scrapeSendToMiraklUrl(entityId);

        log.info("Found URL: {}", secureUrl);

        adminSessionService.adminRequest()
                .get(secureUrl)
                .then()
                .statusCode(200);

        log.info("--- [Admin] Order successfully sent to Mirakl ---");
    }

}