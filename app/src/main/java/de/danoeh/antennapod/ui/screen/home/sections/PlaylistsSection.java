package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.PlaylistEvent;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import de.danoeh.antennapod.ui.screen.playlist.PlaylistFragment;
import de.danoeh.antennapod.ui.screen.playlist.PlaylistListAdapter;
import de.danoeh.antennapod.ui.screen.playlist.PlaylistsFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class PlaylistsSection extends HomeSection {
    public static final String TAG = "PlaylistsSection";
    private PlaylistListAdapter listAdapter;
    private Disposable disposable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        listAdapter = new PlaylistListAdapter() {
            @Override
            protected void onPlaylistClicked(Playlist playlist) {
                ((MainActivity) requireActivity()).loadChildFragment(PlaylistFragment.newInstance(playlist.getId()));
            }
        };
        viewBinding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.recyclerView.setPadding(0, 0, 0, 0);
        viewBinding.recyclerView.setAdapter(listAdapter);
        viewBinding.emptyLabel.setText(R.string.no_playlists_label);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlaylistEvent(PlaylistEvent event) {
        loadItems();
    }

    @Override
    protected String getSectionTitle() {
        return getString(R.string.playlists_label);
    }

    @Override
    protected String getMoreLinkTitle() {
        return getString(R.string.manage_playlists_label);
    }

    @Override
    protected void handleMoreClick() {
        ((MainActivity) requireActivity()).loadChildFragment(new PlaylistsFragment());
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(DBReader::getPlaylists)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlists -> {
                    listAdapter.updateItems(playlists);
                    viewBinding.emptyLabel.setVisibility(playlists.isEmpty() ? View.VISIBLE : View.GONE);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}
