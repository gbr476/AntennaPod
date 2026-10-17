package de.danoeh.antennapod.ui.screen.home.settingsdialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.ui.screen.home.HomeFragment;
import de.danoeh.antennapod.ui.screen.home.sections.PlaylistSection;
import de.danoeh.antennapod.ui.screen.home.sections.PlaylistsSection;

public class HomePreferences {
    private static final String PREF_HIDDEN_SECTIONS = "PrefHomeSectionsString";
    private static final String PREF_SECTION_ORDER = "PrefHomeSectionOrder";
    private static HashMap<String, String> sectionTagToName;

    public static String getNameFromTag(Context context, String sectionTag) {
        if (sectionTagToName == null) {
            initializeMap(context);
        }

        return sectionTagToName.get(sectionTag);
    }

    public static String getNameFromTag(Context context, String sectionTag, @Nullable List<Playlist> playlists) {
        if (PlaylistSection.isPlaylistSectionTag(sectionTag) && playlists != null) {
            long playlistId = PlaylistSection.playlistIdFromTag(sectionTag);
            for (Playlist playlist : playlists) {
                if (playlist.getId() == playlistId) {
                    return context.getString(R.string.home_section_playlist, playlist.getTitle());
                }
            }
        }
        return getNameFromTag(context, sectionTag);
    }

    private static void initializeMap(Context context) {
        Resources resources = context.getResources();
        String[] sectionLabels = resources.getStringArray(R.array.home_section_titles);
        String[] sectionTags = resources.getStringArray(R.array.home_section_tags);

        sectionTagToName = new HashMap<>(sectionTags.length);

        for (int i = 0; i < sectionLabels.length; i++) {
            String label = sectionLabels[i];
            String tag = sectionTags[i];

            sectionTagToName.put(tag, label);
        }
    }

    public static List<String> getHiddenSectionTags(Context context) {
        return getHiddenSectionTags(context, null);
    }

    public static List<String> getHiddenSectionTags(Context context, @Nullable List<Playlist> playlists) {
        List<String> sectionTagOrder = getListPreference(context, PREF_SECTION_ORDER);
        List<String> playlistSectionTags = getPlaylistSectionTags(sectionTagOrder, playlists);
        List<String> hiddenSectionTags = new ArrayList<>();
        for (String sectionTag : getListPreference(context, PREF_HIDDEN_SECTIONS)) {
            if (!PlaylistSection.isPlaylistSectionTag(sectionTag) || playlistSectionTags.contains(sectionTag)) {
                hiddenSectionTags.add(sectionTag);
            }
        }
        List<String> optInSectionTags = new ArrayList<>(playlistSectionTags);
        optInSectionTags.add(0, PlaylistsSection.TAG);
        for (String sectionTag : optInSectionTags) {
            if (!sectionTagOrder.contains(sectionTag) && !hiddenSectionTags.contains(sectionTag)) {
                hiddenSectionTags.add(sectionTag);
            }
        }
        return hiddenSectionTags;
    }

    public static List<String> getSortedSectionTags(Context context) {
        return getSortedSectionTags(context, null);
    }

    public static List<String> getSortedSectionTags(Context context, @Nullable List<Playlist> playlists) {
        List<String> sectionTagOrder = getListPreference(context, PREF_SECTION_ORDER);
        List<String> hiddenSectionTags = getHiddenSectionTags(context, playlists);
        List<String> sectionTags = new ArrayList<>(
                Arrays.asList(context.getResources().getStringArray(R.array.home_section_tags)));
        sectionTags.addAll(getPlaylistSectionTags(sectionTagOrder, playlists));
        Collections.sort(sectionTags, (String a, String b) -> Integer.signum(
                indexOfOrMaxValue(sectionTagOrder, a) - indexOfOrMaxValue(sectionTagOrder, b)));

        List<String> finalSectionTags = new ArrayList<>();
        for (String sectionTag: sectionTags) {
            if (hiddenSectionTags.contains(sectionTag)) {
                continue;
            }

            finalSectionTags.add(sectionTag);
        }

        return finalSectionTags;
    }

    private static List<String> getPlaylistSectionTags(List<String> sectionTagOrder,
                                                       @Nullable List<Playlist> playlists) {
        List<String> playlistSectionTags = new ArrayList<>();
        if (playlists == null) {
            for (String sectionTag : sectionTagOrder) {
                if (PlaylistSection.isPlaylistSectionTag(sectionTag)) {
                    playlistSectionTags.add(sectionTag);
                }
            }
        } else {
            for (Playlist playlist : playlists) {
                playlistSectionTags.add(PlaylistSection.tagForPlaylist(playlist.getId()));
            }
        }
        return playlistSectionTags;
    }

    private static List<String> getListPreference(Context context, String preferenceKey) {
        SharedPreferences prefs = context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE);
        String hiddenSectionsString = prefs.getString(preferenceKey, "");
        return new ArrayList<>(Arrays.asList(TextUtils.split(hiddenSectionsString, ",")));
    }

    private static int indexOfOrMaxValue(List<String> haystack, String needle) {
        int index = haystack.indexOf(needle);
        return index == -1 ? Integer.MAX_VALUE : index;
    }

    public static void saveChanges(Context context, List<String> hiddenSections, List<String> sectionOrder) {
        SharedPreferences prefs = context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(PREF_HIDDEN_SECTIONS, TextUtils.join(",", hiddenSections));
        edit.putString(PREF_SECTION_ORDER, TextUtils.join(",", sectionOrder));
        edit.apply();
    }
}
