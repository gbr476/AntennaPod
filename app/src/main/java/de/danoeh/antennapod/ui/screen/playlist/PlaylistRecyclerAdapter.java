package de.danoeh.antennapod.ui.screen.playlist;

import android.annotation.SuppressLint;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.actionbutton.ItemActionButton;
import de.danoeh.antennapod.actionbutton.PlayActionButton;
import de.danoeh.antennapod.actionbutton.PlayLocalActionButton;
import de.danoeh.antennapod.actionbutton.StreamActionButton;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder;
import de.danoeh.antennapod.ui.swipeactions.SwipeActions;

public class PlaylistRecyclerAdapter extends EpisodeItemListAdapter {
    private final long playlistId;
    private final SwipeActions swipeActions;

    public PlaylistRecyclerAdapter(FragmentActivity mainActivity, long playlistId, SwipeActions swipeActions) {
        super(mainActivity);
        this.playlistId = playlistId;
        this.swipeActions = swipeActions;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void afterBindViewHolder(EpisodeItemViewHolder holder, int pos) {
        if (inActionMode()) {
            holder.dragHandle.setVisibility(View.GONE);
            holder.dragHandle.setOnTouchListener(null);
            holder.coverHolder.setOnTouchListener(null);
            return;
        }
        holder.dragHandle.setVisibility(View.VISIBLE);
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                swipeActions.startDrag(holder);
            }
            return false;
        });
        holder.coverHolder.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                boolean isLtr = holder.itemView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
                float factor = isLtr ? 1 : -1;
                if (factor * event.getX() < factor * 0.5 * v.getWidth()) {
                    swipeActions.startDrag(holder);
                }
            }
            return false;
        });

        final FeedItem item = holder.getFeedItem();
        if (item == null) {
            return;
        }
        ItemActionButton actionButton = ItemActionButton.forItem(item);
        if (actionButton instanceof PlayActionButton || actionButton instanceof PlayLocalActionButton
                || actionButton instanceof StreamActionButton) {
            holder.secondaryActionButton.setOnClickListener(v -> {
                PlaybackPreferences.setCurrentPlaylist(playlistId, item.getId());
                actionButton.onClick(getActivity());
            });
        }
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.playlist_context, menu);
        super.onCreateContextMenu(menu, v, menuInfo);

        if (!inActionMode()) {
            menu.findItem(R.id.multi_select).setVisible(true);
            if (getItem(0).getId() == getLongPressedItem().getId()) {
                menu.findItem(R.id.move_to_top_item).setVisible(false);
            }
            if (getItem(getItemCount() - 1).getId() == getLongPressedItem().getId()) {
                menu.findItem(R.id.move_to_bottom_item).setVisible(false);
            }
        } else {
            menu.findItem(R.id.move_to_top_item).setVisible(false);
            menu.findItem(R.id.move_to_bottom_item).setVisible(false);
        }
    }
}
