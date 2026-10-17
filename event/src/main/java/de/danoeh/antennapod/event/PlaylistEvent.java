package de.danoeh.antennapod.event;

public class PlaylistEvent {
    public static final long ALL_PLAYLISTS = 0;

    public final long playlistId;

    public PlaylistEvent(long playlistId) {
        this.playlistId = playlistId;
    }

    public boolean affects(long playlistId) {
        return this.playlistId == ALL_PLAYLISTS || this.playlistId == playlistId;
    }
}
