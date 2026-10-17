package de.danoeh.antennapod.ui.screen.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;

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
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.PlaylistEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.playback.service.PlaybackStatus;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.MenuItemUtils;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.EmptyViewHandler;
import de.danoeh.antennapod.ui.common.LiftOnScrollListener;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListRecyclerView;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder;
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler;
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler;
import de.danoeh.antennapod.ui.screen.feed.ItemSortDialog;
import de.danoeh.antennapod.ui.swipeactions.SwipeActions;
import de.danoeh.antennapod.ui.view.FloatingSelectMenu;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class PlaylistFragment extends Fragment implements MaterialToolbar.OnMenuItemClickListener,
        EpisodeItemListAdapter.OnSelectModeListener {
    public static final String TAG = "PlaylistFragment";
    private static final String ARG_PLAYLIST_ID = "playlist_id";

    private long playlistId;
    private Playlist playlist;
    private List<FeedItem> items;

    private TextView infoBar;
    private EpisodeItemListRecyclerView recyclerView;
    private PlaylistRecyclerAdapter recyclerAdapter;
    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private FloatingSelectMenu floatingSelectMenu;
    private SwipeActions swipeActions;
    private Disposable disposable;

    public static PlaylistFragment newInstance(long playlistId) {
        PlaylistFragment fragment = new PlaylistFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYLIST_ID, playlistId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistId = requireArguments().getLong(ARG_PLAYLIST_ID);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.queue_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle("");
        toolbar.setOnMenuItemClickListener(this);
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, true);
        toolbar.inflateMenu(R.menu.playlist);

        progressBar = root.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);
        infoBar = root.findViewById(R.id.info_bar);
        int paddingHorizontal = (int) (getResources().getDisplayMetrics().density * 60);
        infoBar.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);

        SwipeRefreshLayout swipeRefreshLayout = root.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setEnabled(false);

        recyclerView = root.findViewById(R.id.recyclerView);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        registerForContextMenu(recyclerView);
        recyclerView.addOnScrollListener(new LiftOnScrollListener(root.findViewById(R.id.appbar)));

        swipeActions = new PlaylistSwipeActions();
        swipeActions.setFilter(FeedItemFilter.unfiltered());
        swipeActions.attachTo(recyclerView);

        recyclerAdapter = new PlaylistRecyclerAdapter((MainActivity) getActivity(), playlistId, swipeActions) {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                super.onCreateContextMenu(menu, v, menuInfo);
                MenuItemUtils.setOnClickListeners(menu, PlaylistFragment.this::onContextItemSelected);
            }

            @Override
            protected void onSelectedItemsUpdated() {
                super.onSelectedItemsUpdated();
                List<FeedItem> selectedItems = getSelectedItems();
                Menu menu = floatingSelectMenu.getMenu();
                FeedItemMenuHandler.onPrepareMenu(menu, selectedItems);
                boolean canMove = !selectedItems.isEmpty() && items != null && selectedItems.size() < items.size();
                menu.findItem(R.id.remove_from_playlist_item).setVisible(!selectedItems.isEmpty());
                menu.findItem(R.id.move_to_top_item).setVisible(canMove);
                menu.findItem(R.id.move_to_bottom_item).setVisible(canMove);
                floatingSelectMenu.updateItemVisibility();
            }
        };
        recyclerAdapter.setOnSelectModeListener(this);
        recyclerView.setAdapter(recyclerAdapter);

        EmptyViewHandler emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
        emptyView.setIcon(R.drawable.ic_playlists);
        emptyView.setTitle(R.string.no_playlist_items_header_label);
        emptyView.setMessage(R.string.no_playlist_items_label);
        emptyView.updateAdapter(recyclerAdapter);
        emptyView.hide();

        floatingSelectMenu = root.findViewById(R.id.floatingSelectMenu);
        floatingSelectMenu.inflate(R.menu.episodes_apply_action_speeddial);
        floatingSelectMenu.setOnMenuItemClickListener(menuItem -> {
            if (recyclerAdapter.getSelectedCount() == 0) {
                EventBus.getDefault().post(new MessageEvent(getString(R.string.no_items_selected_message)));
                return false;
            }
            List<FeedItem> selectedItems = recyclerAdapter.getSelectedItems();
            int itemId = menuItem.getItemId();
            if (itemId == R.id.remove_from_playlist_item) {
                removeWithUndo(selectedItems);
            } else if (itemId == R.id.move_to_top_item) {
                DBWriter.movePlaylistItems(playlistId, selectedItems, true);
            } else if (itemId == R.id.move_to_bottom_item) {
                DBWriter.movePlaylistItems(playlistId, selectedItems, false);
            } else {
                new EpisodeMultiSelectActionHandler(getActivity(), itemId).handleAction(selectedItems);
            }
            recyclerAdapter.endSelectMode();
            return true;
        });
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
    public void onDestroyView() {
        super.onDestroyView();
        if (recyclerAdapter != null) {
            recyclerAdapter.endSelectMode();
        }
        recyclerAdapter = null;
        if (toolbar != null) {
            toolbar.setOnMenuItemClickListener(null);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlaylistEvent(PlaylistEvent event) {
        if (event.affects(playlistId)) {
            loadItems();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        if (items == null || recyclerAdapter == null) {
            return;
        }
        for (FeedItem item : event.items) {
            int pos = FeedItemEvent.indexOfItemWithId(items, item.getId());
            if (pos >= 0) {
                items.set(pos, item);
                recyclerAdapter.notifyItemChangedCompat(pos);
            }
        }
        refreshInfoBar();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(EpisodeDownloadEvent event) {
        if (items == null || recyclerAdapter == null) {
            return;
        }
        for (String downloadUrl : event.getUrls()) {
            int pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, downloadUrl);
            if (pos >= 0) {
                recyclerAdapter.notifyItemChangedCompat(pos);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (recyclerAdapter == null) {
            return;
        }
        for (int i = 0; i < recyclerAdapter.getItemCount(); i++) {
            EpisodeItemViewHolder holder = (EpisodeItemViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null && holder.isPlayingItem()) {
                holder.notifyPlaybackPositionUpdated(event);
                break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        loadItems();
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
                    if (recyclerAdapter == null) {
                        return;
                    }
                    progressBar.setVisibility(View.GONE);
                    if (result.first == null) {
                        return;
                    }
                    playlist = result.first;
                    items = result.second;
                    toolbar.setTitle(playlist.getTitle());
                    toolbar.getMenu().findItem(R.id.playlist_remove_after_played)
                            .setChecked(playlist.shouldRemoveAfterPlayed());
                    toolbar.getMenu().findItem(playlist.isReversePlayOrder()
                            ? R.id.playlist_play_bottom_to_top : R.id.playlist_play_top_to_bottom).setChecked(true);
                    recyclerAdapter.updateItems(items);
                    refreshInfoBar();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void refreshInfoBar() {
        if (items == null) {
            return;
        }
        long timeLeft = 0;
        for (FeedItem item : items) {
            if (item.getMedia() != null) {
                float playbackSpeed = 1;
                if (UserPreferences.timeRespectsSpeed()) {
                    playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia());
                }
                long itemTimeLeft = item.getMedia().getDuration() - item.getMedia().getPosition();
                timeLeft += (long) (itemTimeLeft / playbackSpeed);
            }
        }
        String episodes = getResources().getQuantityString(R.plurals.num_episodes, items.size(), items.size());
        String time = Converter.getDurationStringLocalized(getResources(), timeLeft, false);
        String info = getString(R.string.queue_time_left_label, episodes, time);
        if (playlist != null && playlist.isReversePlayOrder()) {
            info = getString(R.string.playlist_plays_bottom_to_top, info);
        }
        infoBar.setText(info);
        infoBar.setVisibility(recyclerAdapter.inActionMode() ? View.INVISIBLE : View.VISIBLE);
    }

    public long getPlaylistId() {
        return playlistId;
    }

    public void removeWithUndo(List<FeedItem> itemsToRemove) {
        if (items == null) {
            return;
        }
        final List<FeedItem> previousItems = new ArrayList<>(items);
        for (FeedItem item : itemsToRemove) {
            int pos = FeedItemEvent.indexOfItemWithId(items, item.getId());
            if (pos >= 0) {
                items.remove(pos);
                recyclerAdapter.notifyItemRemoved(pos);
            }
        }
        refreshInfoBar();
        DBWriter.removePlaylistItems(playlistId, itemsToRemove);
        String message = getResources().getQuantityString(R.plurals.removed_from_playlist_message,
                itemsToRemove.size(), itemsToRemove.size());
        EventBus.getDefault().post(new MessageEvent(message,
                context -> DBWriter.setPlaylistItems(playlistId, previousItems), getString(R.string.undo)));
    }

    private void playFromStart() {
        if (items == null || items.isEmpty()) {
            return;
        }
        FeedItem firstItem = playlist.isReversePlayOrder() ? items.get(items.size() - 1) : items.get(0);
        if (firstItem.getMedia() == null || PlaybackStatus.isCurrentlyPlaying(firstItem.getMedia())) {
            return;
        }
        ItemActionButton actionButton = ItemActionButton.forItem(firstItem);
        if (!(actionButton instanceof PlayActionButton) && !(actionButton instanceof PlayLocalActionButton)) {
            actionButton = new StreamActionButton(firstItem);
        }
        PlaybackPreferences.setCurrentPlaylist(playlistId, firstItem.getId());
        actionButton.onClick(requireContext());
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (playlist == null) {
            return false;
        }
        final int itemId = item.getItemId();
        if (itemId == R.id.play_playlist_item) {
            playFromStart();
            return true;
        } else if (itemId == R.id.playlist_sort) {
            PlaylistSortDialog.newInstance(playlistId).show(getChildFragmentManager(), "SortDialog");
            return true;
        } else if (itemId == R.id.playlist_play_top_to_bottom || itemId == R.id.playlist_play_bottom_to_top) {
            playlist.setReversePlayOrder(itemId == R.id.playlist_play_bottom_to_top);
            item.setChecked(true);
            DBWriter.setPlaylist(playlist);
            refreshInfoBar();
            return true;
        } else if (itemId == R.id.playlist_remove_after_played) {
            playlist.setRemoveAfterPlayed(!playlist.shouldRemoveAfterPlayed());
            item.setChecked(playlist.shouldRemoveAfterPlayed());
            DBWriter.setPlaylist(playlist);
            return true;
        } else if (itemId == R.id.rename_playlist_item) {
            PlaylistDialogs.showEditDialog(requireContext(), playlist, DBWriter::setPlaylist);
            return true;
        } else if (itemId == R.id.delete_playlist_item) {
            PlaylistDialogs.showDeleteDialog(requireContext(), playlist,
                    () -> getParentFragmentManager().popBackStack());
            return true;
        }
        return false;
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (!isVisible() || recyclerAdapter == null) {
            return false;
        }
        FeedItem selectedItem = recyclerAdapter.getLongPressedItem();
        if (selectedItem == null) {
            return super.onContextItemSelected(item);
        }
        if (recyclerAdapter.onContextItemSelected(item)) {
            return true;
        }
        final int itemId = item.getItemId();
        if (itemId == R.id.move_to_top_item) {
            DBWriter.movePlaylistItems(playlistId, Collections.singletonList(selectedItem), true);
            return true;
        } else if (itemId == R.id.move_to_bottom_item) {
            DBWriter.movePlaylistItems(playlistId, Collections.singletonList(selectedItem), false);
            return true;
        } else if (itemId == R.id.remove_from_playlist_item) {
            removeWithUndo(Collections.singletonList(selectedItem));
            return true;
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, itemId, selectedItem);
    }

    @Override
    public void onStartSelectMode() {
        swipeActions.detach();
        floatingSelectMenu.setVisibility(View.VISIBLE);
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                (int) getResources().getDimension(R.dimen.floating_select_menu_height));
        refreshInfoBar();
    }

    @Override
    public void onEndSelectMode() {
        floatingSelectMenu.setVisibility(View.GONE);
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(), 0);
        swipeActions.attachTo(recyclerView);
        refreshInfoBar();
    }

    public static class PlaylistSortDialog extends ItemSortDialog {

        static PlaylistSortDialog newInstance(long playlistId) {
            PlaylistSortDialog dialog = new PlaylistSortDialog();
            Bundle args = new Bundle();
            args.putLong(ARG_PLAYLIST_ID, playlistId);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        protected void onAddItem(int title, SortOrder ascending, SortOrder descending, boolean ascendingIsDefault) {
            if (ascending == SortOrder.EPISODE_FILENAME_A_Z || ascending == SortOrder.SIZE_SMALL_LARGE) {
                return;
            }
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.SMART_SHUFFLE_OLD_NEW) {
                ascendingIsDefault = true;
            }
            super.onAddItem(title, ascending, descending, ascendingIsDefault);
        }

        @Override
        protected void onSelectionChanged() {
            super.onSelectionChanged();
            if (sortOrder != null) {
                DBWriter.sortPlaylist(requireArguments().getLong(ARG_PLAYLIST_ID), sortOrder);
            }
        }
    }

    private class PlaylistSwipeActions extends SwipeActions {
        private int dragFrom = -1;
        private int dragTo = -1;

        PlaylistSwipeActions() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, PlaylistFragment.this, TAG);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (items == null || from >= items.size() || to >= items.size() || from < 0 || to < 0) {
                return false;
            }
            if (dragFrom == -1) {
                dragFrom = from;
            }
            dragTo = to;
            items.add(to, items.remove(from));
            recyclerAdapter.notifyItemMoved(from, to);
            return true;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                DBWriter.movePlaylistItem(playlistId, dragFrom, dragTo, false);
            }
            dragFrom = -1;
            dragTo = -1;
        }
    }
}
