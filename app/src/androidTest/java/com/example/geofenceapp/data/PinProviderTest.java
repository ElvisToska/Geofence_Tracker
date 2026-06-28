package com.example.geofenceapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PinProviderTest {
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(
            GeofenceProvider.class,
            GeofenceContract.AUTHORITY)
            .build();

    @Test
    public void insertAndDeletePins_workForUser() {
        ContentResolver resolver = providerRule.getResolver();

        ContentValues user = new ContentValues();
        user.put(GeofenceContract.Users.USERNAME, "pinuser");
        user.put(GeofenceContract.Users.PASSWORD_HASH, "hash");
        user.put(GeofenceContract.Users.PASSWORD_SALT, "salt");
        user.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_USER);
        assertNotNull(resolver.insert(GeofenceContract.Users.URI, user));

        ContentValues pin = new ContentValues();
        pin.put(GeofenceContract.Pins.USERNAME, "pinuser");
        pin.put(GeofenceContract.Pins.LABEL, "Home");
        pin.put(GeofenceContract.Pins.LATITUDE, 37.98);
        pin.put(GeofenceContract.Pins.LONGITUDE, 23.72);
        pin.put(GeofenceContract.Pins.RADIUS_METERS, 50.0);
        assertNotNull(resolver.insert(GeofenceContract.Pins.URI, pin));

        Cursor cursor = resolver.query(GeofenceContract.Pins.byUsernameUri("pinuser"), null, null, null, null);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("Home", cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Pins.LABEL)));
        } finally {
            cursor.close();
        }

        int rows = resolver.delete(GeofenceContract.Pins.byUsernameUri("pinuser"),
                GeofenceContract.Pins.LABEL + " = ?",
                new String[]{"Home"});
        assertEquals(1, rows);
    }
}
