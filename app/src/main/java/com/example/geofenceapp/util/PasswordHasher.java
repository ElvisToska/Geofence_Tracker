package com.example.geofenceapp.util;

import android.util.Base64;
import android.util.Log;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Handles secure password hashing and verification using PBKDF2-WithHmacSHA256.
 *
 * Passwords are never stored in plaintext. Each user gets a unique random salt,
 * and the password is hashed with 12,000 iterations of PBKDF2 to produce
 * a 256-bit key. Both salt and hash are stored as Base64 strings in the database.
 */
public final class PasswordHasher {

    private static final String TAG = "PasswordHasher";

    /** Length of the random salt in bytes (16 bytes = 128 bits). */
    private static final int SALT_BYTES = 16;

    /** Output key length in bits (256 bits). */
    private static final int KEY_BITS = 256;

    /** Number of PBKDF2 iterations — balances security vs. performance on mobile. */
    private static final int ITERATIONS = 12000;

    /** Private constructor — this class is used only through its static methods. */
    private PasswordHasher() {
    }

    /**
     * Generates a cryptographically secure random salt.
     *
     * @return a Base64-encoded 16-byte random salt
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    /**
     * Hashes a password with the given salt using PBKDF2-WithHmacSHA256.
     *
     * @param password   the plaintext password to hash
     * @param saltBase64 the Base64-encoded salt
     * @return the Base64-encoded password hash
     * @throws IllegalStateException if the hashing algorithm is unavailable
     */
    public static String hashPassword(String password, String saltBase64) {
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hash password", e);
            throw new IllegalStateException("Could not hash password", e);
        }
    }

    /**
     * Verifies a plaintext password against a stored salt and hash.
     *
     * @param password   the plaintext password to verify
     * @param saltBase64 the Base64-encoded salt from the database
     * @param hashBase64 the Base64-encoded hash from the database
     * @return true if the password matches, false otherwise
     */
    public static boolean verify(String password, String saltBase64, String hashBase64) {
        return hashPassword(password, saltBase64).equals(hashBase64);
    }
}
