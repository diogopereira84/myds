package com.fedex.automation.utils;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class FedExEncryptionUtil {

    /**
     * Generates the encrypted credit card string required by the FedEx/Magento backend.
     * Replicates the assumed frontend logic.
     *
     * Payload format:
     *   M{ccNumber}={YY}{MM}:{CVV}
     *
     * Example:
     *   M4111111111111111=3502:471
     */
    public static String encryptCreditCard(
            String ccNumber,
            String month,
            String year,
            String cvv,
            String publicKeyPEM
    ) throws Exception {

        String yy = year.length() > 2 ? year.substring(2) : year;
        String payload = "M" + ccNumber + "=" + yy + month + ":" + cvv;

        String cleanKey = publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(cleanKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] encryptedBytes = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
