package de.danoeh.antennapod.ui.screen.home.settingsdialog;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.ui.screen.home.HomeFragment;
import de.danoeh.antennapod.ui.screen.home.sections.PlaylistSection;
import de.danoeh.antennapod.ui.screen.home.sections.PlaylistsSection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class HomePreferencesTest {
    private static final List<String> DEFAULT_SECTIONS = Arrays.asList("QueueSection", "InboxSection",
            "EpisodesSurpriseSection", "SubscriptionsSection", "DownloadsSection");

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void testPlaylistSectionsAreHiddenByDefault() {
        List<Playlist> playlists = Collections.singletonList(new Playlist(1, "Commute", false, false, 0));

        assertEquals(DEFAULT_SECTIONS, HomePreferences.getSortedSectionTags(context));
        assertEquals(DEFAULT_SECTIONS, HomePreferences.getSortedSectionTags(context, playlists));
        assertEquals(Arrays.asList(PlaylistsSection.TAG, PlaylistSection.tagForPlaylist(1)),
                HomePreferences.getHiddenSectionTags(context, playlists));
    }

    @Test
    public void testShownPlaylistSectionsAppearInOrder() {
        String playlistTag = PlaylistSection.tagForPlaylist(1);
        HomePreferences.saveChanges(context, Collections.singletonList("DownloadsSection"),
                Arrays.asList("QueueSection", playlistTag, PlaylistsSection.TAG, "InboxSection",
                        "EpisodesSurpriseSection", "SubscriptionsSection", "DownloadsSection"));

        assertEquals(Arrays.asList("QueueSection", playlistTag, PlaylistsSection.TAG, "InboxSection",
                "EpisodesSurpriseSection", "SubscriptionsSection"), HomePreferences.getSortedSectionTags(context));
    }

    @Test
    public void testDeletedPlaylistIsDroppedFromDialog() {
        String deletedTag = PlaylistSection.tagForPlaylist(2);
        HomePreferences.saveChanges(context, Collections.singletonList(deletedTag),
                Arrays.asList("QueueSection", deletedTag));
        List<Playlist> playlists = Collections.singletonList(new Playlist(1, "Commute", false, false, 0));

        List<String> hidden = HomePreferences.getHiddenSectionTags(context, playlists);
        assertFalse(hidden.contains(deletedTag));
        assertTrue(hidden.contains(PlaylistSection.tagForPlaylist(1)));
        assertFalse(HomePreferences.getSortedSectionTags(context, playlists).contains(deletedTag));
    }

    @Test
    public void testPlaylistSectionName() {
        List<Playlist> playlists = Collections.singletonList(new Playlist(1, "Commute", false, false, 0));

        assertEquals("Playlist: Commute",
                HomePreferences.getNameFromTag(context, PlaylistSection.tagForPlaylist(1), playlists));
        assertEquals("Playlists", HomePreferences.getNameFromTag(context, PlaylistsSection.TAG, playlists));
    }
}
