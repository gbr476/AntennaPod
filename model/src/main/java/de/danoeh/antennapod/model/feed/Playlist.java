package de.danoeh.antennapod.model.feed;

public class Playlist {
    private long id;
    private String title;
    private boolean removeAfterPlayed;
    private boolean reversePlayOrder;
    private final int episodeCount;

    public Playlist(long id, String title, boolean removeAfterPlayed, boolean reversePlayOrder, int episodeCount) {
        this.id = id;
        this.title = title;
        this.removeAfterPlayed = removeAfterPlayed;
        this.reversePlayOrder = reversePlayOrder;
        this.episodeCount = episodeCount;
    }

    public Playlist(String title, boolean removeAfterPlayed) {
        this(0, title, removeAfterPlayed, false, 0);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean shouldRemoveAfterPlayed() {
        return removeAfterPlayed;
    }

    public void setRemoveAfterPlayed(boolean removeAfterPlayed) {
        this.removeAfterPlayed = removeAfterPlayed;
    }

    public boolean isReversePlayOrder() {
        return reversePlayOrder;
    }

    public void setReversePlayOrder(boolean reversePlayOrder) {
        this.reversePlayOrder = reversePlayOrder;
    }

    public int getEpisodeCount() {
        return episodeCount;
    }
}
