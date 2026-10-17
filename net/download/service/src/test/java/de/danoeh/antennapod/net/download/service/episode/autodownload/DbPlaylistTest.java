package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DbPlaylistTest {
    private static final long TIMEOUT = 5L;

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());

        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
        DBWriter.tearDownTests();
    }

    @Test
    public void testCreatePlaylistWithItems() throws Exception {
        List<FeedItem> items = createTestFeed(3).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, items).get(TIMEOUT, TimeUnit.SECONDS);

        assertTrue(playlist.getId() != 0);
        Playlist fromDb = DBReader.getPlaylist(playlist.getId());
        assertNotNull(fromDb);
        assertEquals("Commute", fromDb.getTitle());
        assertFalse(fromDb.shouldRemoveAfterPlayed());
        assertEquals(3, fromDb.getEpisodeCount());
        assertEquals(toItemIds(items), toItemIds(DBReader.getPlaylistItems(playlist.getId())));
    }

    @Test
    public void testAddPlaylistItemsIgnoresDuplicatesAndItemsWithoutMedia() throws Exception {
        List<FeedItem> items = createTestFeed(3).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, Collections.singletonList(items.get(0))).get(TIMEOUT, TimeUnit.SECONDS);

        FeedItem withoutMedia = new FeedItem(0, "no media", "no media", "link", new Date(), FeedItem.PLAYED,
                items.get(0).getFeed());
        DBWriter.addPlaylistItems(playlist.getId(), Arrays.asList(items.get(1), items.get(0), withoutMedia))
                .get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(items.get(0).getId(), items.get(1).getId()),
                toItemIds(DBReader.getPlaylistItems(playlist.getId())));
    }

    @Test
    public void testPlaylistsAreIndependent() throws Exception {
        List<FeedItem> items = createTestFeed(3).getItems();
        Playlist first = new Playlist("First", false);
        Playlist second = new Playlist("Second", false);
        DBWriter.createPlaylist(first, Arrays.asList(items.get(0), items.get(1))).get(TIMEOUT, TimeUnit.SECONDS);
        DBWriter.createPlaylist(second, Arrays.asList(items.get(2), items.get(0))).get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(items.get(0).getId(), items.get(1).getId()),
                toItemIds(DBReader.getPlaylistItems(first.getId())));
        assertEquals(Arrays.asList(items.get(2).getId(), items.get(0).getId()),
                toItemIds(DBReader.getPlaylistItems(second.getId())));
        assertTrue(DBReader.getQueue().isEmpty());

        List<Playlist> playlists = DBReader.getPlaylists();
        assertEquals(2, playlists.size());
        assertEquals("First", playlists.get(0).getTitle());
        assertEquals("Second", playlists.get(1).getTitle());
    }

    @Test
    public void testMovePlaylistItem() throws Exception {
        List<FeedItem> items = createTestFeed(4).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, items).get(TIMEOUT, TimeUnit.SECONDS);

        DBWriter.movePlaylistItem(playlist.getId(), 0, 2, false).get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(items.get(1).getId(), items.get(2).getId(), items.get(0).getId(),
                items.get(3).getId()), toItemIds(DBReader.getPlaylistItems(playlist.getId())));
    }

    @Test
    public void testMovePlaylistItemsToTopAndBottom() throws Exception {
        List<FeedItem> items = createTestFeed(4).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, items).get(TIMEOUT, TimeUnit.SECONDS);

        DBWriter.movePlaylistItems(playlist.getId(), Arrays.asList(items.get(2), items.get(3)), true)
                .get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(items.get(2).getId(), items.get(3).getId(), items.get(0).getId(),
                items.get(1).getId()), toItemIds(DBReader.getPlaylistItems(playlist.getId())));

        DBWriter.movePlaylistItems(playlist.getId(), Collections.singletonList(items.get(2)), false)
                .get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(items.get(3).getId(), items.get(0).getId(), items.get(1).getId(),
                items.get(2).getId()), toItemIds(DBReader.getPlaylistItems(playlist.getId())));
    }

    @Test
    public void testRemovePlaylistItems() throws Exception {
        List<FeedItem> items = createTestFeed(3).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, items).get(TIMEOUT, TimeUnit.SECONDS);

        DBWriter.removePlaylistItems(playlist.getId(), Collections.singletonList(items.get(1)))
                .get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(items.get(0).getId(), items.get(2).getId()),
                toItemIds(DBReader.getPlaylistItems(playlist.getId())));
    }

    @Test
    public void testGetNextInPlaylist() throws Exception {
        List<FeedItem> items = createTestFeed(3).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, Arrays.asList(items.get(2), items.get(0), items.get(1)))
                .get(TIMEOUT, TimeUnit.SECONDS);

        FeedItem next = DBReader.getNextInPlaylist(playlist.getId(), items.get(2));
        assertNotNull(next);
        assertEquals(items.get(0).getId(), next.getId());
        next = DBReader.getNextInPlaylist(playlist.getId(), items.get(0));
        assertNotNull(next);
        assertEquals(items.get(1).getId(), next.getId());
        assertNull(DBReader.getNextInPlaylist(playlist.getId(), items.get(1)));
    }

    @Test
    public void testGetNextInPlaylistBottomToTop() throws Exception {
        List<FeedItem> items = createTestFeed(3).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, items).get(TIMEOUT, TimeUnit.SECONDS);
        playlist.setReversePlayOrder(true);
        DBWriter.setPlaylist(playlist).get(TIMEOUT, TimeUnit.SECONDS);

        Playlist fromDb = DBReader.getPlaylist(playlist.getId());
        assertNotNull(fromDb);
        assertTrue(fromDb.isReversePlayOrder());
        FeedItem next = DBReader.getNextInPlaylist(playlist.getId(), items.get(2));
        assertNotNull(next);
        assertEquals(items.get(1).getId(), next.getId());
        next = DBReader.getNextInPlaylist(playlist.getId(), items.get(1));
        assertNotNull(next);
        assertEquals(items.get(0).getId(), next.getId());
        assertNull(DBReader.getNextInPlaylist(playlist.getId(), items.get(0)));
    }

    @Test
    public void testRemovePlayedItemOnlyFromPlaylistsWithSettingEnabled() throws Exception {
        List<FeedItem> items = createTestFeed(2).getItems();
        Playlist removing = new Playlist("Removing", true);
        Playlist keeping = new Playlist("Keeping", false);
        DBWriter.createPlaylist(removing, items).get(TIMEOUT, TimeUnit.SECONDS);
        DBWriter.createPlaylist(keeping, items).get(TIMEOUT, TimeUnit.SECONDS);

        DBWriter.removePlayedItemFromPlaylists(items.get(0)).get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(Collections.singletonList(items.get(1).getId()),
                toItemIds(DBReader.getPlaylistItems(removing.getId())));
        assertEquals(toItemIds(items), toItemIds(DBReader.getPlaylistItems(keeping.getId())));
    }

    @Test
    public void testSetPlaylistUpdatesSettings() throws Exception {
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, Collections.emptyList()).get(TIMEOUT, TimeUnit.SECONDS);

        playlist.setTitle("Gym");
        playlist.setRemoveAfterPlayed(true);
        DBWriter.setPlaylist(playlist).get(TIMEOUT, TimeUnit.SECONDS);

        Playlist fromDb = DBReader.getPlaylist(playlist.getId());
        assertNotNull(fromDb);
        assertEquals("Gym", fromDb.getTitle());
        assertTrue(fromDb.shouldRemoveAfterPlayed());
        assertFalse(fromDb.isReversePlayOrder());
    }

    @Test
    public void testDeletePlaylist() throws Exception {
        List<FeedItem> items = createTestFeed(2).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, items).get(TIMEOUT, TimeUnit.SECONDS);

        DBWriter.deletePlaylist(playlist.getId()).get(TIMEOUT, TimeUnit.SECONDS);

        assertNull(DBReader.getPlaylist(playlist.getId()));
        assertTrue(DBReader.getPlaylistItems(playlist.getId()).isEmpty());
        assertEquals(0, DBReader.getAllPlaylistItemIds().size());
        assertNotNull(DBReader.getFeedItem(items.get(0).getId()));
    }

    @Test
    public void testDeleteFeedItemsRemovesThemFromPlaylists() throws Exception {
        List<FeedItem> items = createTestFeed(2).getItems();
        Playlist playlist = new Playlist("Commute", false);
        DBWriter.createPlaylist(playlist, items).get(TIMEOUT, TimeUnit.SECONDS);

        DBWriter.deleteFeedItems(context, Collections.singletonList(items.get(0))).get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(Collections.singletonList(items.get(1).getId()),
                toItemIds(DBReader.getPlaylistItems(playlist.getId())));
        assertEquals(1, DBReader.getAllPlaylistItemIds().size());
    }

    private static Feed createTestFeed(int numItems) {
        Feed feed = new Feed("url", null, "title");
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "title " + i, "id " + i, "link " + i,
                    new Date(), FeedItem.PLAYED, feed);
            item.setMedia(new FeedMedia(item, "", 0, ""));
            feed.getItems().add(item);
        }
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        for (FeedItem item : feed.getItems()) {
            assertTrue(item.getId() != 0);
        }
        return feed;
    }

    private static List<Long> toItemIds(List<FeedItem> items) {
        List<Long> itemIds = new ArrayList<>(items.size());
        for (FeedItem item : items) {
            itemIds.add(item.getId());
        }
        return itemIds;
    }
}
