package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.actionbutton.ItemActionButton;
import de.danoeh.antennapod.actionbutton.PlayActionButton;
import de.danoeh.antennapod.actionbutton.PlayLocalActionButton;
import de.danoeh.antennapod.actionbutton.StreamActionButton;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.EpisodeDownloadEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.PlaylistEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.MenuItemUtils;
import de.danoeh.antennapod.ui.episodeslist.HorizontalItemListAdapter;
import de.danoeh.antennapod.ui.episodeslist.HorizontalItemViewHolder;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import de.danoeh.antennapod.ui.screen.playlist.PlaylistFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class PlaylistSection extends HomeSection {
    public static final String TAG = "PlaylistSection";
    public static final String TAG_PREFIX = "PlaylistSection:";
    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private static final int NUM_EPISODES = 8;
    private long playlistId;
    private HorizontalItemListAdapter listAdapter;
    private Disposable disposable;
    private List<FeedItem> items = new ArrayList<>();

    public static String tagForPlaylist(long playlistId) {
        return TAG_PREFIX + playlistId;
    }

    public static boolean isPlaylistSectionTag(String tag) {
        return tag.startsWith(TAG_PREFIX);
    }

    public static long playlistIdFromTag(String tag) {
        return Long.parseLong(tag.substring(TAG_PREFIX.length()));
    }

    public static PlaylistSection newInstance(long playlistId) {
        PlaylistSection section = new PlaylistSection();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYLIST_ID, playlistId);
        section.setArguments(args);
        return section;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistId = requireArguments().getLong(ARG_PLAYLIST_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        listAdapter = new HorizontalItemListAdapter((MainActivity) getActivity()) {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                super.onCreateContextMenu(menu, v, menuInfo);
                MenuItemUtils.setOnClickListeners(menu, PlaylistSection.this::onContextItemSelected);
            }

            @Override
            public void onBindViewHolder(@NonNull HorizontalItemViewHolder holder, int position) {
                super.onBindViewHolder(holder, position);
                if (position >= items.size()) {
                    return;
                }
                final FeedItem item = items.get(position);
                ItemActionButton actionButton = ItemActionButton.forItem(item);
                if (actionButton instanceof PlayActionButton || actionButton instanceof PlayLocalActionButton
                        || actionButton instanceof StreamActionButton) {
                    holder.secondaryActionIcon.setOnClickListener(v -> {
                        PlaybackPreferences.setCurrentPlaylist(playlistId, item.getId());
                        actionButton.onClick(requireContext());
                    });
                }
            }
        };
        listAdapter.setDummyViews(NUM_EPISODES);
        viewBinding.recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        viewBinding.recyclerView.setAdapter(listAdapter);
        int paddingHorizontal = (int) (12 * getResources().getDisplayMetrics().density);
        viewBinding.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
        viewBinding.emptyLabel.setText(R.string.no_playlist_items_label);
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
        if (event.affects(playlistId)) {
            loadItems();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        for (FeedItem item : event.items) {
            int pos = FeedItemEvent.indexOfItemWithId(items, item.getId());
            if (pos >= 0) {
                items.set(pos, item);
                listAdapter.notifyItemChangedCompat(pos);
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(EpisodeDownloadEvent event) {
        for (String downloadUrl : event.getUrls()) {
            int pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, downloadUrl);
            if (pos >= 0) {
                listAdapter.notifyItemChangedCompat(pos);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (listAdapter == null) {
            return;
        }
        for (int i = 0; i < listAdapter.getItemCount(); i++) {
            HorizontalItemViewHolder holder = (HorizontalItemViewHolder)
                    viewBinding.recyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null && holder.isCurrentlyPlayingItem()) {
                holder.notifyPlaybackPositionUpdated(event);
                break;
            }
        }
    }

    @Override
    protected String getSectionTitle() {
        return "";
    }

    @Override
    protected String getMoreLinkTitle() {
        return getString(R.string.open_playlist_label);
    }

    @Override
    protected void handleMoreClick() {
        ((MainActivity) requireActivity()).loadChildFragment(PlaylistFragment.newInstance(playlistId));
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> new Pair<>(DBReader.getPlaylist(playlistId),
                        DBReader.getPlaylistItems(playlistId)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.first == null) {
                        viewBinding.getRoot().setVisibility(View.GONE);
                        return;
                    }
                    viewBinding.getRoot().setVisibility(View.VISIBLE);
                    viewBinding.titleLabel.setText(result.first.getTitle());
                    List<FeedItem> playOrder = new ArrayList<>(result.second);
                    if (result.first.isReversePlayOrder()) {
                        Collections.reverse(playOrder);
                    }
                    items = new ArrayList<>(playOrder.subList(0, Math.min(NUM_EPISODES, playOrder.size())));
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(items);
                    viewBinding.emptyLabel.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}
