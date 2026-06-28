package com.example.geofenceapp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.data.GeofenceProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuthManagerTest {
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(
            GeofenceProvider.class,
            GeofenceContract.AUTHORITY)
            .build();

    @Test
    public void adminAccount_isSeededAndCanLogin() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        AuthManager.ensureSeedAdmin(ApplicationProvider.getApplicationContext());

        assertTrue(AuthManager.login(ApplicationProvider.getApplicationContext(), AuthManager.ADMIN_USERNAME, AuthManager.ADMIN_PASSWORD));
        assertTrue(AuthManager.isAdmin(ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void signupLoginAndLogout_workForRegularUser() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        boolean created = AuthManager.signUp(ApplicationProvider.getApplicationContext(), "tester1", "secure123");
        assertTrue(created);

        assertTrue(AuthManager.login(ApplicationProvider.getApplicationContext(), "tester1", "secure123"));
        assertEquals("tester1", AppPrefs.getAuthUsername(ApplicationProvider.getApplicationContext()));
        assertFalse(AppPrefs.getAuthToken(ApplicationProvider.getApplicationContext()).isEmpty());

        AuthManager.logout(ApplicationProvider.getApplicationContext());
        assertFalse(AppPrefs.isLoggedIn(ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void restoreSession_rehydratesLoggedInUserFromPrefs() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        assertTrue(AuthManager.signUp(ApplicationProvider.getApplicationContext(), "persistme", "secure123"));
        assertTrue(AuthManager.login(ApplicationProvider.getApplicationContext(), "persistme", "secure123"));

        // Simulate process restart: in-memory session is gone, prefs persist.
        AuthSession.clear();
        assertEquals("guest", AuthManager.currentUsername(ApplicationProvider.getApplicationContext()));

        AuthManager.restoreSession(ApplicationProvider.getApplicationContext());
        assertEquals("persistme", AuthManager.currentUsername(ApplicationProvider.getApplicationContext()));
        assertTrue(AuthSession.isLoggedIn());
    }

    @Test
    public void loginRejectsWrongPassword() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        assertTrue(AuthManager.signUp(ApplicationProvider.getApplicationContext(), "wrongpass", "secure123"));
        assertFalse(AuthManager.login(ApplicationProvider.getApplicationContext(), "wrongpass", "bad-password"));
    }

    @Test
    public void signupRejectsShortPassword() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        assertFalse(AuthManager.signUp(ApplicationProvider.getApplicationContext(), "shortpw", "123"));
    }

    @Test
    public void regularUser_isNotAdmin() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        assertTrue(AuthManager.signUp(ApplicationProvider.getApplicationContext(), "plainuser", "secure123"));
        assertTrue(AuthManager.login(ApplicationProvider.getApplicationContext(), "plainuser", "secure123"));
        assertFalse(AuthManager.isAdmin(ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void signupRejectsDuplicateUsers() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        assertTrue(AuthManager.signUp(ApplicationProvider.getApplicationContext(), "dupuser", "secure123"));
        assertFalse(AuthManager.signUp(ApplicationProvider.getApplicationContext(), "dupuser", "secure123"));
    }

    @Test
    public void providerStoresUserRecords() {
        AppPrefs.clearAuth(ApplicationProvider.getApplicationContext());
        ContentResolver resolver = providerRule.getResolver();
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.USERNAME, "provideruser");
        values.put(GeofenceContract.Users.PASSWORD_HASH, "hash");
        values.put(GeofenceContract.Users.PASSWORD_SALT, "salt");
        values.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_USER);
        assertTrue(resolver.insert(GeofenceContract.Users.URI, values) != null);

        Cursor cursor = resolver.query(GeofenceContract.Users.byUsernameUri("provideruser"), null, null, null, null);
        assertTrue(cursor != null);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("provideruser", cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Users.USERNAME)));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
