package com.fedex.automation.utils;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class FedExEncryptionUtil {

    /**
     * Generates the encrypted credit card string required by FedEx backend.
     * Logic replicated from: app/code/Fedex/Pay/view/frontend/web/js/view/paymentScreen.js
     *
     * @param ccNumber  16-digit card number
     * @param month     2-digit month (e.g. "02")
     * @param year      4-digit year (e.g. "2035")
     * @param cvv       CVV code (e.g. "471")
     * @param publicKeyPEM The RSA Public Key string (PEM format)
     * @return Base64 encoded encrypted string
     */
    public static String encryptCreditCard(String ccNumber, String month, String year, String cvv, String publicKeyPEM) throws Exception {
        // 1. Format the Year to 2 digits
        String yy = year.length() > 2 ? year.substring(2) : year;

        // 2. Construct the Payload: M{number}={yy}{mm}:{cvv}
        // Example: M4111111111111111=3502:123
        String payload = "M" + ccNumber + "=" + yy + month + ":" + cvv;

        // 3. Clean and Decode the Public Key
        String cleanKey = publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", ""); // Remove newlines/spaces

        byte[] keyBytes = Base64.getDecoder().decode(cleanKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);

        // 4. Encrypt using RSA/ECB/PKCS1Padding
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(payload.getBytes("UTF-8"));

        // 5. Return Base64 String
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}