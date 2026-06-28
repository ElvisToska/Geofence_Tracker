package com.example.geofenceapp.util;

public final class AuthSession {
    private static String currentUsername = "guest";
    private static String currentToken = "";

    private AuthSession() {
    }

    public static void set(String username, String token) {
        currentUsername = username == null || username.isEmpty() ? "guest" : username;
        currentToken = token == null ? "" : token;
    }

    public static String username() {
        return currentUsername;
    }

    public static String token() {
        return currentToken;
    }

    public static boolean isLoggedIn() {
        return !"guest".equals(currentUsername) && !currentToken.isEmpty();
    }

    public static void clear() {
        currentUsername = "guest";
        currentToken = "";
    }
}
