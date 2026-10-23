package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
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
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DbNextFeedEpisodeTest {
    private static final long TIMEOUT = 5L;

    private Feed feed;
    private FeedItem current;
    private FeedItem playedNewer;
    private FeedItem streamOnlyNewer;
    private FeedItem downloadedNewest;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());

        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        feed = new Feed("url", null, "title", null, null);
        feed.setItems(new ArrayList<>());
        addItem(feed, "older", 50, FeedItem.UNPLAYED, true);
        current = addItem(feed, "current", 100, FeedItem.UNPLAYED, true);
        playedNewer = addItem(feed, "played", 200, FeedItem.PLAYED, true);
        streamOnlyNewer = addItem(feed, "stream only", 300, FeedItem.NEW, false);
        downloadedNewest = addItem(feed, "downloaded", 400, FeedItem.UNPLAYED, true);

        Feed otherFeed = new Feed("other url", null, "other title");
        otherFeed.setItems(new ArrayList<>());
        addItem(otherFeed, "other feed", 150, FeedItem.UNPLAYED, true);

        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.setCompleteFeed(otherFeed);
        adapter.close();
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
        DBWriter.tearDownTests();
    }

    @Test
    public void testNextNewerUnplayedEpisodeWhenStreamingAllowed() {
        FeedItem next = DBReader.getNextUnplayedFeedItem(current, false);
        assertNotNull(next);
        assertEquals(streamOnlyNewer.getId(), next.getId());
    }

    @Test
    public void testSkipsToNextDownloadedEpisodeWhenStreamingNotAllowed() {
        FeedItem next = DBReader.getNextUnplayedFeedItem(current, true);
        assertNotNull(next);
        assertEquals(downloadedNewest.getId(), next.getId());
    }

    @Test
    public void testNoNewerEpisode() {
        assertNull(DBReader.getNextUnplayedFeedItem(downloadedNewest, false));
        assertNull(DBReader.getNextUnplayedFeedItem(downloadedNewest, true));
    }

    @Test
    public void testEpisodesWithSamePublishDate() {
        Feed sameDateFeed = new Feed("same date url", null, "same date title");
        sameDateFeed.setItems(new ArrayList<>());
        FeedItem first = addItem(sameDateFeed, "first", 500, FeedItem.UNPLAYED, true);
        FeedItem second = addItem(sameDateFeed, "second", 500, FeedItem.UNPLAYED, true);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(sameDateFeed);
        adapter.close();

        FeedItem next = DBReader.getNextUnplayedFeedItem(first, false);
        assertNotNull(next);
        assertEquals(second.getId(), next.getId());
        assertNull(DBReader.getNextUnplayedFeedItem(second, false));
    }

    @Test
    public void testPlayNextEpisodeSettingIsStored() throws Exception {
        FeedPreferences preferences = feed.getPreferences();
        assertEquals(FeedPreferences.PlayNextEpisode.GLOBAL, preferences.getPlayNextEpisode());
        assertTrue(preferences.isPlayNextEpisode(true));
        assertFalse(preferences.isPlayNextEpisode(false));

        preferences.setPlayNextEpisode(FeedPreferences.PlayNextEpisode.ALWAYS);
        DBWriter.setFeedPreferences(preferences).get(TIMEOUT, TimeUnit.SECONDS);

        Feed fromDb = DBReader.getFeed(feed.getId(), false, 0, 0);
        assertNotNull(fromDb);
        assertEquals(FeedPreferences.PlayNextEpisode.ALWAYS, fromDb.getPreferences().getPlayNextEpisode());
        assertTrue(fromDb.getPreferences().isPlayNextEpisode(false));
    }

    private static FeedItem addItem(Feed feed, String title, long pubDate, int state, boolean downloaded) {
        FeedItem item = new FeedItem(0, title, title, "link " + title, new Date(pubDate), state, feed);
        if (downloaded) {
            item.setMedia(new FeedMedia(0, item, 1000, 0, 1L, "audio/mpeg", "/file/" + title,
                    "url " + title, System.currentTimeMillis(), null, 0, 0));
        } else {
            item.setMedia(new FeedMedia(item, "url " + title, 1L, "audio/mpeg"));
        }
        feed.getItems().add(item);
        return item;
    }
}
