package de.danoeh.antennapod.ui.screen.playlist;

import android.content.res.Resources;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.PlaylistListItemBinding;
import de.danoeh.antennapod.model.feed.Playlist;

public class PlaylistListAdapter extends RecyclerView.Adapter<PlaylistListAdapter.PlaylistViewHolder> {
    private List<Playlist> playlists = new ArrayList<>();

    public void updateItems(List<Playlist> playlists) {
        this.playlists = playlists;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PlaylistViewHolder(PlaylistListItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        Resources resources = holder.itemView.getResources();
        holder.binding.titleLabel.setText(playlist.getTitle());
        String summary = resources.getQuantityString(R.plurals.num_episodes,
                playlist.getEpisodeCount(), playlist.getEpisodeCount());
        if (playlist.shouldRemoveAfterPlayed()) {
            summary = resources.getString(R.string.playlist_removes_played_episodes, summary);
        }
        holder.binding.summaryLabel.setText(summary);
        holder.itemView.setOnClickListener(v -> onPlaylistClicked(playlist));
        holder.itemView.setOnCreateContextMenuListener(
                (menu, v, menuInfo) -> onCreatePlaylistContextMenu(menu, playlist));
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    protected void onPlaylistClicked(Playlist playlist) {
    }

    protected void onCreatePlaylistContextMenu(ContextMenu menu, Playlist playlist) {
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        final PlaylistListItemBinding binding;

        PlaylistViewHolder(PlaylistListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
