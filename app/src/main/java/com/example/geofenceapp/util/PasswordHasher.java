package com.example.geofenceapp.util;

import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 12000;

    private PasswordHasher() {
    }

    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hashPassword(String password, String saltBase64) {
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash password", e);
        }
    }

    public static boolean verify(String password, String saltBase64, String hashBase64) {
        return hashPassword(password, saltBase64).equals(hashBase64);
    }
}
