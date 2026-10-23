package de.danoeh.antennapod.storage.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DBUpgraderPlaylistTest {
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        db = SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE Feeds (id INTEGER PRIMARY KEY AUTOINCREMENT ,title TEXT)");
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void testUpgradeAddsPlayOrderToExistingPlaylists() {
        db.execSQL("CREATE TABLE Playlists (id INTEGER PRIMARY KEY AUTOINCREMENT ,title TEXT,"
                + "remove_after_played INTEGER DEFAULT 0)");
        db.execSQL("INSERT INTO Playlists (title, remove_after_played) VALUES ('Commute', 1)");

        DBUpgrader.upgrade(db, 3120000, 3120002);

        try (Cursor cursor = db.rawQuery(
                "SELECT title, remove_after_played, reverse_play_order FROM Playlists", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Commute", cursor.getString(0));
            assertEquals(1, cursor.getInt(1));
            assertEquals(0, cursor.getInt(2));
        }
    }

    @Test
    public void testUpgradeAddsPlayNextEpisodeToExistingFeeds() {
        db.execSQL("INSERT INTO Feeds (title) VALUES ('Podcast')");

        DBUpgrader.upgrade(db, 3120001, 3120002);

        try (Cursor cursor = db.rawQuery("SELECT title, play_next_episode FROM Feeds", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Podcast", cursor.getString(0));
            assertEquals(0, cursor.getInt(1));
        }
    }

    @Test
    public void testUpgradeFromBeforePlaylistsCreatesTables() {
        DBUpgrader.upgrade(db, 3110000, 3120002);

        db.execSQL("INSERT INTO Playlists (title) VALUES ('Commute')");
        db.execSQL("INSERT INTO PlaylistItems (playlist, feeditem, position) VALUES (1, 2, 0)");
        try (Cursor cursor = db.rawQuery("SELECT remove_after_played, reverse_play_order FROM Playlists", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0));
            assertEquals(0, cursor.getInt(1));
        }
    }
}
