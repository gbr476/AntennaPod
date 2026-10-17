package de.danoeh.antennapod.ui.screen.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Collections;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.PlaylistEvent;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.MenuItemUtils;
import de.danoeh.antennapod.ui.common.EmptyViewHandler;
import de.danoeh.antennapod.ui.common.LiftOnScrollListener;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class PlaylistsFragment extends Fragment implements MaterialToolbar.OnMenuItemClickListener {
    public static final String TAG = "PlaylistsFragment";
    private static final String KEY_UP_ARROW = "up_arrow";

    private PlaylistListAdapter adapter;
    private ProgressBar progressBar;
    private Disposable disposable;
    private boolean displayUpArrow;
    private Playlist longPressedPlaylist;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.playlists_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this);
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);
        toolbar.inflateMenu(R.menu.playlists);

        progressBar = root.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        RecyclerView recyclerView = root.findViewById(R.id.recyclerView);
        recyclerView.addOnScrollListener(new LiftOnScrollListener(root.findViewById(R.id.appbar)));
        adapter = new PlaylistListAdapter() {
            @Override
            protected void onPlaylistClicked(Playlist playlist) {
                ((MainActivity) requireActivity()).loadChildFragment(PlaylistFragment.newInstance(playlist.getId()));
            }

            @Override
            protected void onCreatePlaylistContextMenu(ContextMenu menu, Playlist playlist) {
                longPressedPlaylist = playlist;
                requireActivity().getMenuInflater().inflate(R.menu.playlists_context, menu);
                menu.setHeaderTitle(playlist.getTitle());
                MenuItemUtils.setOnClickListeners(menu, PlaylistsFragment.this::onContextItemSelected);
            }
        };
        recyclerView.setAdapter(adapter);

        EmptyViewHandler emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
        emptyView.setIcon(R.drawable.ic_playlists);
        emptyView.setTitle(R.string.no_playlists_header_label);
        emptyView.setMessage(R.string.no_playlists_label);
        emptyView.setButtonText(R.string.new_playlist_label);
        emptyView.setButtonVisibility(View.VISIBLE);
        emptyView.setButtonOnClickListener(v -> createPlaylist());
        emptyView.updateAdapter(adapter);
        emptyView.hide();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlaylistEvent(PlaylistEvent event) {
        loadItems();
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(DBReader::getPlaylists)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlists -> {
                    progressBar.setVisibility(View.GONE);
                    adapter.updateItems(playlists);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void createPlaylist() {
        PlaylistDialogs.showEditDialog(requireContext(), null,
                playlist -> DBWriter.createPlaylist(playlist, Collections.emptyList()));
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.new_playlist_item) {
            createPlaylist();
            return true;
        }
        return false;
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (longPressedPlaylist == null) {
            return super.onContextItemSelected(item);
        }
        if (item.getItemId() == R.id.rename_playlist_item) {
            PlaylistDialogs.showEditDialog(requireContext(), longPressedPlaylist, DBWriter::setPlaylist);
            return true;
        } else if (item.getItemId() == R.id.delete_playlist_item) {
            PlaylistDialogs.showDeleteDialog(requireContext(), longPressedPlaylist, () -> { });
            return true;
        }
        return super.onContextItemSelected(item);
    }
}
