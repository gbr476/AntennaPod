package de.danoeh.antennapod.ui.screen.playlist;

import android.app.Activity;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;

public class AddToPlaylistDialog {
    private static final String TAG = "AddToPlaylistDialog";

    private AddToPlaylistDialog() {
    }

    public static void show(Activity activity, List<FeedItem> items) {
        Observable.fromCallable(DBReader::getPlaylists)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlists -> showDialog(activity, items, playlists),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static void showDialog(Activity activity, List<FeedItem> items, List<Playlist> playlists) {
        if (activity.isFinishing()) {
            return;
        }
        if (playlists.isEmpty()) {
            showCreateDialog(activity, items);
            return;
        }
        String[] titles = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            titles[i] = playlists.get(i).getTitle();
        }
        final boolean[] checked = new boolean[playlists.size()];
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.add_to_playlist_label)
                .setMultiChoiceItems(titles, checked, (d, which, isChecked) -> {
                    checked[which] = isChecked;
                    ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isAnyChecked(checked));
                })
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    List<Playlist> selectedPlaylists = new ArrayList<>();
                    for (int i = 0; i < playlists.size(); i++) {
                        if (checked[i]) {
                            selectedPlaylists.add(playlists.get(i));
                            DBWriter.addPlaylistItems(playlists.get(i).getId(), items);
                        }
                    }
                    showAddedMessage(activity, selectedPlaylists, items);
                })
                .setNeutralButton(R.string.new_playlist_label, (d, which) -> showCreateDialog(activity, items))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    private static boolean isAnyChecked(boolean[] checked) {
        for (boolean isChecked : checked) {
            if (isChecked) {
                return true;
            }
        }
        return false;
    }

    private static void showCreateDialog(Activity activity, List<FeedItem> items) {
        PlaylistDialogs.showEditDialog(activity, null, playlist -> {
            DBWriter.createPlaylist(playlist, items);
            showAddedMessage(activity, Collections.singletonList(playlist), items);
        });
    }

    private static void showAddedMessage(Activity activity, List<Playlist> playlists, List<FeedItem> items) {
        int count = 0;
        for (FeedItem item : items) {
            if (item.hasMedia()) {
                count++;
            }
        }
        String message;
        if (playlists.size() == 1) {
            message = activity.getResources().getQuantityString(
                    R.plurals.added_to_playlist_message, count, count, playlists.get(0).getTitle());
        } else {
            message = activity.getResources().getQuantityString(
                    R.plurals.added_to_playlists_message, count, count, playlists.size());
        }
        EventBus.getDefault().post(new MessageEvent(message));
    }
}
