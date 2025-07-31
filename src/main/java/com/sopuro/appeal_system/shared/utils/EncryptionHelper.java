package com.sopuro.appeal_system.shared.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Base64;

@Slf4j
public class EncryptionHelper {
    private static final String SECRET_KEY = System.getenv("AES_SECRET_KEY");
    private static final String SALT = System.getenv("AES_SALT");
    private static final Integer ITERATION_COUNT = 65536;
    private static final Integer KEY_LENGTH = 256;

    public static String generateCaseAccessCode(String caseId, String requesterId) {
        if (caseId == null || caseId.isBlank() || requesterId == null || requesterId.isBlank())
            throw new IllegalArgumentException("Case ID and Requester ID must not be null or blank");

        String plainText = String.format("%s/%s/%s", caseId, requesterId, Instant.now());

        try {
            SecureRandom secureRandom = new SecureRandom();
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), SALT.getBytes(), ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKeySpec = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivspec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] encryptedData = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encryptedData, 0, iv.length);
            System.arraycopy(cipherText, 0, encryptedData, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception ex) {
            log.error("An error has occurred while trying encrypt case access code", ex);
            return null;
        }
    }

    public static CaseAccessDetails decryptCaseAccessCode(String accessCode) {
        if (accessCode == null || accessCode.isBlank())
            throw new IllegalArgumentException("Access Code must not be null or blank.");

        try {
            byte[] encryptedData = Base64.getDecoder().decode(accessCode);
            byte[] iv = new byte[16];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), SALT.getBytes(), ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKeySpec = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivspec);

            byte[] cipherText = new byte[encryptedData.length - 16];
            System.arraycopy(encryptedData, 16, cipherText, 0, cipherText.length);

            byte[] decryptedText = cipher.doFinal(cipherText);
            String decryptedStr = new String(decryptedText, StandardCharsets.UTF_8);

            String[] strComponents = decryptedStr.split("/");
            return new CaseAccessDetails(strComponents[0], strComponents[1], Instant.parse(strComponents[2]));
        } catch (Exception ex) {
            // Invalid access code, ignore
            return null;
        }
    }

    public record CaseAccessDetails(String caseId, String createdBy, Instant creationTimestamp) {}
}
