package de.danoeh.antennapod.ui.screen.playlist;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Consumer;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.PlaylistEditDialogBinding;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.storage.database.DBWriter;

public class PlaylistDialogs {

    private PlaylistDialogs() {
    }

    public static void showEditDialog(Context context, @Nullable Playlist playlist, Consumer<Playlist> onSave) {
        PlaylistEditDialogBinding binding = PlaylistEditDialogBinding.inflate(LayoutInflater.from(context));
        if (playlist != null) {
            binding.nameInput.setText(playlist.getTitle());
            binding.removeAfterPlayedCheckbox.setVisibility(View.GONE);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(playlist == null ? R.string.new_playlist_label : R.string.rename_playlist_label)
                .setView(binding.getRoot())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String title = binding.nameInput.getText().toString().trim();
                    if (playlist == null) {
                        onSave.accept(new Playlist(title, binding.removeAfterPlayedCheckbox.isChecked()));
                    } else {
                        playlist.setTitle(title);
                        onSave.accept(playlist);
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(binding.nameInput.getText().toString().trim().length() > 0);
        binding.nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                positiveButton.setEnabled(s.toString().trim().length() > 0);
            }
        });
    }

    public static void showDeleteDialog(Context context, Playlist playlist, Runnable onDeleted) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_playlist_label)
                .setMessage(context.getString(R.string.delete_playlist_confirmation_msg, playlist.getTitle()))
                .setPositiveButton(R.string.delete_label, (dialog, which) -> {
                    DBWriter.deletePlaylist(playlist.getId());
                    onDeleted.run();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }
}
